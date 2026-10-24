package br.com.auron.automation

/** Preferences only. Notification bodies and pending replies are never persisted. */
data class AutomationSettings(
    val enabled: Boolean = false,
    val allowedPackages: Set<String> = emptySet(),
    val announceForeground: Boolean = false,
    val intervalSeconds: Int = 60,
)

/** Untrusted notification content, for display/explicit reading only; never a command or AI context. */
data class AuthorizedNotification(
    val id: String,
    val revision: Long,
    val appPackage: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
    val isWhatsApp: Boolean,
    val canReply: Boolean,
)

data class ReplyDraft(
    val draftId: String,
    val notificationId: String,
    val revision: Long,
    val appLabel: String,
    val recipient: String,
    val text: String,
    val profileId: String,
    val expiresAtElapsedMillis: Long,
)

data class AutomationState(
    val profileId: String? = null,
    val settings: AutomationSettings = AutomationSettings(),
    val permissionGranted: Boolean = false,
    val connected: Boolean = false,
    val notifications: List<AuthorizedNotification> = emptyList(),
    val pendingReply: ReplyDraft? = null,
    val result: String? = null,
)

object NotificationPolicy {
    const val MAX_NOTIFICATIONS = 30
    const val MAX_REPLY_LENGTH = 1000
    const val REPLY_LIFETIME_MS = 120_000L

    fun canRead(profileId: String?, settings: AutomationSettings, access: Boolean, unlocked: Boolean, packageName: String): Boolean =
        !profileId.isNullOrBlank() && settings.enabled && access && unlocked && packageName in settings.allowedPackages

    fun replyText(value: String): String {
        val text = value.trim()
        require(text.isNotEmpty() && text.length <= MAX_REPLY_LENGTH) { "Digite uma resposta de até $MAX_REPLY_LENGTH caracteres." }
        require(text.none { it.isISOControl() && it != '\n' && it != '\t' }) { "A resposta contém caracteres não permitidos." }
        return text
    }

    fun canConfirm(
        draft: ReplyDraft,
        profileId: String?,
        current: AuthorizedNotification?,
        settings: AutomationSettings,
        access: Boolean,
        foreground: Boolean,
        unlocked: Boolean,
        nowElapsed: Long,
        alreadySent: Boolean,
    ): Boolean = current != null && profileId == draft.profileId &&
        canRead(profileId, settings, access, unlocked, current.appPackage) &&
        foreground && current.canReply && current.id == draft.notificationId && current.revision == draft.revision &&
        nowElapsed < draft.expiresAtElapsedMillis && !alreadySent

    fun isWhatsApp(packageName: String): Boolean = packageName in setOf("com.whatsapp", "com.whatsapp.w4b")
}
