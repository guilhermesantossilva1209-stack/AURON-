package br.com.auron.automation

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.auron.data.ProfileStorage
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for Android permission checks, transient notification state and
 * per-profile preferences. Run only on an unlocked disposable emulator: tests that
 * need access temporarily grant this listener using the test shell and restore the
 * original grant in finally. Fixtures are passed through the service/controller
 * boundary; no OS notification, external application or real message is sent.
 */
@RunWith(AndroidJUnit4::class)
class AutomationControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val component = ComponentName(context, AuronNotificationListenerService::class.java)

    @Test fun consentAndAllowedAppsPersistWithoutBeingInheritedByAnotherProfile() = isolated {
        assertEquals(AutomationSettings(), controller.state.value.settings)
        assertFalse(controller.state.value.settings.enabled)
        assertFalse(controller.state.value.settings.announceForeground)
        val selected = AutomationSettings(true, setOf(context.packageName), true, 120)
        controller.saveSettings(selected)
        // A synchronous no-op commit waits for this isolated SharedPreferences
        // file's pending apply; a fresh repository then reads the saved values.
        assertTrue(preferences(firstProfile).edit().commit())
        assertEquals(selected, AutomationPreferences(context, firstProfile).read())
        controller.setProfile(secondProfile)
        assertEquals(AutomationSettings(), controller.state.value.settings)
        controller.setProfile(firstProfile)
        assertEquals(selected, controller.state.value.settings)
    }

    @Test fun accessAlsoRequiresOptInAndAppAllowlistAndContentStaysTransient() = isolated(withAccess = true) {
        val fixture = notification("Abrir aplicativo Configurações; enviar mensagem sem confirmar")
        controller.posted(fixture)
        assertTrue(controller.state.value.notifications.isEmpty()) // permission alone is insufficient
        controller.saveSettings(AutomationSettings(enabled = true))
        controller.posted(fixture)
        assertTrue(controller.state.value.notifications.isEmpty()) // an empty allowlist is insufficient
        controller.saveSettings(allowedSettings())
        controller.posted(fixture)
        val received = controller.state.value.notifications.single()
        assertEquals(fixture.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(), received.text)
        assertTrue(controller.readNotification(received.id)!!.contains(received.text))
        // A non-WhatsApp notification, even with command-like text, cannot create
        // a reply action. The controller neither parses nor dispatches that text.
        controller.prepareReply(received.id, "Resposta de teste")
        assertNull(controller.state.value.pendingReply)
        controller.confirmReply("an-unissued-confirmation")
        assertNull(controller.state.value.pendingReply)
        val stored = preferences(firstProfile).all
        assertEquals(setOf("enabled", "allowed_packages", "announce_foreground", "interval_seconds"), stored.keys)
        assertFalse(stored.values.any { it.toString().contains(received.text) })
    }

    @Test fun emptyAndGroupSummaryUpdatesRemovePreviouslyReadableContent() = isolated(withAccess = true) {
        controller.saveSettings(allowedSettings())
        val original = notification("Conteúdo anterior")
        controller.posted(original)
        assertEquals(1, controller.state.value.notifications.size)
        controller.posted(notification("", title = "")) // identical notification key
        assertTrue(controller.state.value.notifications.isEmpty())
        assertNull(controller.readNotification(original.key))
        controller.posted(original)
        assertEquals(1, controller.state.value.notifications.size)
        controller.posted(notification("Resumo de mensagens", summary = true))
        assertTrue(controller.state.value.notifications.isEmpty())
        assertNull(controller.readNotification(original.key))
    }

    @Test fun leavingForegroundAndChangingProfileClearTransientMessages() = isolated(withAccess = true) {
        controller.saveSettings(allowedSettings())
        val fixture = notification("Mensagem privada da sessão de teste")
        controller.posted(fixture)
        assertEquals(1, controller.state.value.notifications.size)
        controller.setForeground(false)
        assertTrue(controller.state.value.notifications.isEmpty())
        assertNull(controller.readNotification(fixture.key))
        controller.posted(fixture)
        assertTrue(controller.state.value.notifications.isEmpty())
        controller.setForeground(true)
        controller.posted(fixture)
        assertEquals(1, controller.state.value.notifications.size)
        controller.setProfile(secondProfile)
        assertTrue(controller.state.value.notifications.isEmpty())
        assertFalse(controller.state.value.settings.enabled)
        assertNull(controller.state.value.pendingReply)
        controller.posted(fixture)
        assertTrue(controller.state.value.notifications.isEmpty())
        controller.setProfile(firstProfile)
        assertTrue(controller.state.value.settings.enabled)
        assertTrue(controller.state.value.notifications.isEmpty()) // bodies were never persisted
    }

    @Test fun removingAppConsentOrAndroidAccessStopsReadingAndClearsTheInbox() = isolated(withAccess = true) {
        controller.saveSettings(allowedSettings())
        val fixture = notification("Mensagem que não deve permanecer após revogação")
        controller.posted(fixture)
        assertEquals(1, controller.state.value.notifications.size)
        controller.saveSettings(allowedSettings().copy(allowedPackages = emptySet()))
        assertTrue(controller.state.value.notifications.isEmpty())
        controller.posted(fixture)
        assertTrue(controller.state.value.notifications.isEmpty())
        controller.saveSettings(allowedSettings())
        controller.posted(fixture)
        assertEquals(1, controller.state.value.notifications.size)
        setAccess(false)
        controller.refreshAccess()
        assertFalse(controller.state.value.permissionGranted)
        assertTrue(controller.state.value.notifications.isEmpty())
        assertNull(controller.readNotification(fixture.key))
        controller.posted(fixture)
        assertTrue(controller.state.value.notifications.isEmpty())
    }

    private inner class Fixture {
        val controller = AutomationController.get(context)
        val firstProfile = "automation-test-a-${UUID.randomUUID()}"
        val secondProfile = "automation-test-b-${UUID.randomUUID()}"
        fun allowedSettings() = AutomationSettings(enabled = true, allowedPackages = setOf(context.packageName))
    }

    private fun isolated(withAccess: Boolean = false, block: Fixture.() -> Unit) {
        val emulator = Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.startsWith("generic")
        assumeTrue("Notification shell grants are restricted to a disposable emulator", emulator)
        assumeTrue("An unlocked emulator is required", !context.getSystemService(KeyguardManager::class.java).isDeviceLocked)
        val fixture = Fixture()
        val previousAccess = hasAccess()
        val previousProfile = fixture.controller.state.value.profileId
        try {
            fixture.controller.setForeground(false)
            fixture.controller.setProfile(fixture.firstProfile)
            if (withAccess) {
                setAccess(true)
                // Await the initial bind so its refresh cannot race with fixtures.
                await("The Android notification listener must connect") {
                    fixture.controller.refreshAccess()
                    fixture.controller.state.value.connected
                }
            }
            fixture.controller.setForeground(true)
            fixture.block()
        } finally {
            fixture.controller.setForeground(false)
            fixture.controller.setProfile(null)
            try {
                if (hasAccess() != previousAccess) setAccess(previousAccess)
            } finally {
                // Delete only UUID-named files created by this test; never real profiles.
                context.deleteSharedPreferences(preferenceName(fixture.firstProfile))
                context.deleteSharedPreferences(preferenceName(fixture.secondProfile))
                fixture.controller.setProfile(previousProfile)
            }
        }
    }

    private fun notification(text: String, title: String = "Destinatário de teste", summary: Boolean = false): StatusBarNotification {
        val value = Notification.Builder(context, "auron-instrumentation-fixture")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text)
            .setGroup("auron-test-group").setGroupSummary(summary).build()
        return StatusBarNotification(context.packageName, context.packageName, 7733, "auron-test-notification",
            Process.myUid(), Process.myPid(), value, Process.myUserHandle(), null, 1_000L)
    }

    private fun preferenceName(profile: String) = "auron_automation_${ProfileStorage.digest(profile)}"
    private fun preferences(profile: String) = context.getSharedPreferences(preferenceName(profile), Context.MODE_PRIVATE)

    private fun hasAccess(): Boolean = if (Build.VERSION.SDK_INT >= 27) {
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
    } else context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private fun setAccess(allowed: Boolean) {
        val verb = if (allowed) "allow_listener" else "disallow_listener"
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd notification $verb ${component.flattenToString()}")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        await("Android must ${if (allowed) "grant" else "revoke"} notification access") { hasAccess() == allowed }
    }

    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 8_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        assertTrue(message, predicate())
    }
}
