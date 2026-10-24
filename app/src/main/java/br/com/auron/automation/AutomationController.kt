package br.com.auron.automation

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** A generic foreground notice; message bodies are never emitted to AI/history. */
data class AutomationNotice(val profileId: String, val text: String)

/**
 * In-memory, foreground-only notification inbox. Android owns the permission;
 * AURON additionally requires a profile opt-in and explicit package allowlist.
 * No notification payload, sender, or reply draft is persisted or logged.
 */
class AutomationController private constructor(context: Context) {
    private val context = context.applicationContext
    private val mutableState = MutableStateFlow(AutomationState())
    val state = mutableState.asStateFlow()
    private val mutableNotices = MutableSharedFlow<AutomationNotice>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notices = mutableNotices.asSharedFlow()
    private var preferences: AutomationPreferences? = null
    private var listener: AuronNotificationListenerService? = null
    private var foreground = false
    private var revision = 0L
    private var lastNoticeAt = Long.MIN_VALUE
    private val actions = mutableMapOf<String, Notification.Action>()

    @Synchronized
    fun setProfile(profileId: String?) {
        if (profileId == state.value.profileId) return
        actions.clear()
        preferences = profileId?.takeIf { it.isNotBlank() }?.let { AutomationPreferences(context, it) }
        lastNoticeAt = Long.MIN_VALUE
        mutableState.value = AutomationState(
            profileId = profileId?.takeIf { it.isNotBlank() },
            settings = preferences?.read() ?: AutomationSettings(),
            permissionGranted = hasAccess(), connected = listener != null,
        )
        loadCurrent()
    }

    @Synchronized
    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) clearSensitive() else refreshAccess()
    }

    @Synchronized
    fun saveSettings(settings: AutomationSettings) {
        if (state.value.profileId == null) return
        val safe = settings.copy(
            allowedPackages = settings.allowedPackages.filter { it.matches(Regex("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+")) }.take(100).toSet(),
            intervalSeconds = settings.intervalSeconds.coerceIn(30, 3600),
        )
        preferences?.save(safe)
        clearSensitive()
        mutableState.value = state.value.copy(settings = safe)
        loadCurrent()
    }

    @Synchronized
    fun refreshAccess() {
        val access = hasAccess()
        mutableState.value = state.value.copy(permissionGranted = access, connected = listener != null && access)
        if (!access || !unlocked() || !foreground || !state.value.settings.enabled) clearSensitive()
        else loadCurrent()
    }

    /** Invoke only after the owner explicitly requests to manage Android access. */
    fun openAccessSettings(): String? {
        if (!foreground) return "Abra o AURON antes de gerenciar o acesso às notificações."
        return try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        } catch (_: RuntimeException) {
            "Abra Configurações do Android → Acesso especial → Acesso às notificações e selecione AURON."
        }
    }

    @Synchronized
    fun readNotification(id: String): String? {
        val notification = state.value.notifications.firstOrNull { it.id == id } ?: return null
        if (!mayRead(notification.appPackage)) {
            clearSensitive()
            return null
        }
        return listOf(notification.appLabel, notification.title, notification.text).filter { it.isNotBlank() }.joinToString(". ")
    }

    @Synchronized
    fun prepareReply(notificationId: String, text: String) {
        val notification = state.value.notifications.firstOrNull { it.id == notificationId }
        if (notification == null || !mayRead(notification.appPackage) || !notification.canReply || actions[notificationId] == null) {
            mutableState.value = state.value.copy(pendingReply = null, result = "Essa notificação não oferece uma resposta autorizada. Abra o WhatsApp para responder.")
            return
        }
        val reply = try { NotificationPolicy.replyText(text) } catch (error: IllegalArgumentException) {
            mutableState.value = state.value.copy(pendingReply = null, result = error.message)
            return
        }
        mutableState.value = state.value.copy(pendingReply = ReplyDraft(
            draftId = UUID.randomUUID().toString(), notificationId = notification.id,
            revision = notification.revision, appLabel = notification.appLabel,
            recipient = notification.title.ifBlank { notification.appLabel }, text = reply,
            profileId = requireNotNull(state.value.profileId),
            expiresAtElapsedMillis = SystemClock.elapsedRealtime() + NotificationPolicy.REPLY_LIFETIME_MS,
        ), result = null)
    }

    /** A separate UI confirmation must pass the currently displayed draft ID. */
    @Synchronized
    fun confirmReply(draftId: String) {
        val draft = state.value.pendingReply ?: return
        if (draft.draftId != draftId) return
        val current = state.value.notifications.firstOrNull { it.id == draft.notificationId }
        val action = actions[draft.notificationId]
        val valid = NotificationPolicy.canConfirm(draft, state.value.profileId, current,
            state.value.settings, hasAccess(), foreground, unlocked(), SystemClock.elapsedRealtime(), alreadySent = false)
        // Consume before sending; repeats, re-entrant callbacks and old buttons cannot send again.
        mutableState.value = state.value.copy(pendingReply = null)
        if (!valid || action == null || action.actionIntent.creatorPackage != current?.appPackage) {
            mutableState.value = state.value.copy(result = "A confirmação expirou ou a notificação mudou. Prepare a resposta novamente.")
            return
        }
        val inputs = action.remoteInputs?.filter { it.allowFreeFormInput }.orEmpty()
        if (inputs.isEmpty()) {
            mutableState.value = state.value.copy(result = "O WhatsApp não disponibilizou uma resposta por esta notificação.")
            return
        }
        val result = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, draft.text) } }
        val intent = Intent()
        RemoteInput.addResultsToIntent(inputs.toTypedArray(), intent, result)
        if (Build.VERSION.SDK_INT >= 28) RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
        try {
            action.actionIntent.send(context, 0, intent)
            // Android accepting a PendingIntent does not prove server delivery/read status.
            mutableState.value = state.value.copy(result = "Resposta encaminhada ao WhatsApp. Confira a entrega no aplicativo.")
        } catch (_: PendingIntent.CanceledException) {
            mutableState.value = state.value.copy(result = "A ação expirou. Abra o WhatsApp para responder.")
        } catch (_: RuntimeException) {
            mutableState.value = state.value.copy(result = "O Android não permitiu encaminhar a resposta. Abra o WhatsApp.")
        }
    }

    @Synchronized fun cancelReply() { mutableState.value = state.value.copy(pendingReply = null) }
    @Synchronized fun clearResult() { mutableState.value = state.value.copy(result = null) }

    @Synchronized
    internal fun listenerConnected(service: AuronNotificationListenerService) {
        listener = service
        refreshAccess()
    }

    @Synchronized
    internal fun listenerDisconnected(service: AuronNotificationListenerService) {
        if (listener !== service) return
        listener = null
        clearSensitive()
        mutableState.value = state.value.copy(connected = false, permissionGranted = hasAccess())
    }

    @Synchronized
    internal fun posted(sbn: StatusBarNotification) = accept(sbn, announce = true)

    @Synchronized
    internal fun removed(id: String) {
        actions.remove(id)
        mutableState.value = state.value.copy(
            notifications = state.value.notifications.filterNot { it.id == id },
            pendingReply = state.value.pendingReply?.takeUnless { it.notificationId == id },
        )
    }

    private fun loadCurrent() {
        if (!foreground || !unlocked() || !hasAccess() || !state.value.settings.enabled) return
        val active = try { listener?.activeNotifications } catch (_: RuntimeException) { null } ?: return
        val ids = active.map { it.key }.toSet()
        state.value.notifications.filterNot { it.id in ids }.forEach { removed(it.id) }
        active.sortedBy { it.postTime }.forEach { accept(it, announce = false) }
    }

    private fun accept(sbn: StatusBarNotification, announce: Boolean) {
        if (!mayRead(sbn.packageName)) {
            if (!hasAccess() || !unlocked() || !foreground) clearSensitive()
            return
        }
        val raw = sbn.notification ?: run { removed(sbn.key); return }
        if (raw.flags and Notification.FLAG_GROUP_SUMMARY != 0) { removed(sbn.key); return }
        val extras = raw.extras ?: Bundle.EMPTY
        val label = try {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: RuntimeException) { sbn.packageName } catch (_: android.content.pm.PackageManager.NameNotFoundException) { sbn.packageName }
        val title = safeText(extras.getCharSequence(Notification.EXTRA_TITLE), 160)
        val text = safeText(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n"), 3000)
        if (title.isBlank() && text.isBlank()) { removed(sbn.key); return }
        val action = if (NotificationPolicy.isWhatsApp(sbn.packageName)) raw.actions?.firstOrNull {
            it.actionIntent != null && it.actionIntent.creatorPackage == sbn.packageName &&
                it.remoteInputs?.any { input -> input.allowFreeFormInput } == true &&
                (Build.VERSION.SDK_INT < 28 || it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY)
        } else null
        val old = state.value.notifications.firstOrNull { it.id == sbn.key }
        val oldAction = actions[sbn.key]
        val sameReplyAction = oldAction?.actionIntent == action?.actionIntent &&
            oldAction?.remoteInputs?.map { it.resultKey to it.allowFreeFormInput } ==
            action?.remoteInputs?.map { it.resultKey to it.allowFreeFormInput }
        if (!announce && sameReplyAction && old != null && old.title == title && old.text == text && old.postedAtMillis == sbn.postTime && old.canReply == (action != null)) {
            if (action != null) actions[sbn.key] = action
            return
        }
        val item = AuthorizedNotification(sbn.key, ++revision, sbn.packageName, safeText(label, 160), title, text,
            sbn.postTime, NotificationPolicy.isWhatsApp(sbn.packageName), action != null)
        if (action == null) actions.remove(item.id) else actions[item.id] = action
        val items = (listOf(item) + state.value.notifications.filterNot { it.id == item.id })
            .sortedByDescending { it.postedAtMillis }.take(NotificationPolicy.MAX_NOTIFICATIONS)
        actions.keys.retainAll(items.map { it.id }.toSet())
        mutableState.value = state.value.copy(notifications = items,
            pendingReply = state.value.pendingReply?.takeUnless { it.notificationId == item.id })
        val now = SystemClock.elapsedRealtime()
        if (announce && state.value.settings.announceForeground &&
            (lastNoticeAt == Long.MIN_VALUE || now - lastNoticeAt >= state.value.settings.intervalSeconds * 1000L)) {
            lastNoticeAt = now
            mutableNotices.tryEmit(AutomationNotice(requireNotNull(state.value.profileId), "Nova notificação de ${item.appLabel}. Abra Automações para consultar."))
        }
    }

    private fun mayRead(packageName: String) = foreground && NotificationPolicy.canRead(
        state.value.profileId, state.value.settings, hasAccess(), unlocked(), packageName,
    )

    private fun clearSensitive() {
        actions.clear()
        mutableState.value = state.value.copy(notifications = emptyList(), pendingReply = null, result = null)
    }

    private fun hasAccess(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 27) context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, AuronNotificationListenerService::class.java))
        else context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    } catch (_: RuntimeException) { false }

    private fun unlocked(): Boolean = try {
        !context.getSystemService(KeyguardManager::class.java).isDeviceLocked
    } catch (_: RuntimeException) { false }

    private fun safeText(value: CharSequence?, limit: Int) = value?.toString().orEmpty()
        .filter { !it.isISOControl() || it == '\n' || it == '\t' }.trim().take(limit)

    companion object {
        @Volatile private var instance: AutomationController? = null
        fun get(context: Context): AutomationController = instance ?: synchronized(this) {
            instance ?: AutomationController(context).also { instance = it }
        }
    }
}
