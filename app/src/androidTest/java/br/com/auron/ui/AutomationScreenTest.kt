package br.com.auron.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.auron.automation.*
import br.com.auron.ui.theme.AuronTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AutomationScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app = LaunchableApp("com.whatsapp", "WhatsApp")
    private val notification = AuthorizedNotification("message", 1, app.packageName, app.label, "Contato autorizado", "Mensagem recebida", 1L, true, true)
    private val allowed = AutomationState(
        profileId = "local", settings = AutomationSettings(enabled = true, allowedPackages = setOf(app.packageName)),
        permissionGranted = true, connected = true, notifications = listOf(notification),
    )

    @Test fun accessSettingsRequireExplicitConfirmationAndOpeningDoesNotEnableReading() {
        var opens = 0
        var saves = 0
        compose.setContent { AuronTheme {
            AutomationScreen(AutomationState(profileId = "local"), listOf(app), { saves++ }, { opens++ }, {}, {}, { _, _ -> }, {}, {}, {})
        } }
        assertEquals(0, opens)
        assertEquals(0, saves)
        compose.onNodeWithTag("automation_enabled").assertIsOff()
        compose.onNodeWithTag("automation_access").performScrollTo().performClick()
        assertEquals(0, opens)
        compose.onNodeWithTag("automation_access_confirm").performClick()
        assertEquals(1, opens)
        assertEquals(0, saves)
    }

    @Test fun staleNotificationIsNotShownAfterAccessRevocation() {
        val current = mutableStateOf(allowed)
        var reads = 0
        compose.setContent { AuronTheme {
            AutomationScreen(current.value, listOf(app), {}, {}, {}, { reads++ }, { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithTag("screen_automation").performScrollToNode(hasTestTag("automation_read_message"))
        compose.onNodeWithTag("automation_read_message").assertExists()
        compose.runOnIdle { current.value = allowed.copy(permissionGranted = false) }
        compose.onNodeWithTag("automation_read_message").assertDoesNotExist()
        compose.onNodeWithText("Mensagem recebida").assertDoesNotExist()
        assertEquals(0, reads)
    }

    @Test fun preparedReplyNeedsSeparateConfirmationAndCannotBeSubmittedTwice() {
        val current = mutableStateOf(allowed)
        var sends = 0
        var prepares = 0
        compose.setContent { AuronTheme {
            AutomationScreen(current.value, listOf(app), {}, {}, {}, {}, { id, text ->
                prepares++
                current.value = allowed.copy(pendingReply = ReplyDraft("draft", id, 1, app.label, notification.title, text, "local", Long.MAX_VALUE))
            }, { id -> assertEquals("draft", id); sends++ }, { current.value = allowed }, {})
        } }
        compose.onNodeWithTag("screen_automation").performScrollToNode(hasTestTag("automation_reply_message"))
        compose.onNodeWithTag("automation_reply_message").performClick()
        compose.onNodeWithTag("automation_prepare_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("automation_reply_text").performTextInput("Chego às 18 horas.")
        compose.onNodeWithTag("automation_prepare_confirm").performClick()
        assertEquals(1, prepares)
        assertEquals(0, sends)
        compose.onNodeWithTag("automation_reply_preview").assertTextEquals("Chego às 18 horas.")
        compose.onNodeWithTag("automation_send_confirm").performClick()
        assertEquals(1, sends)
        compose.onNodeWithTag("automation_send_confirm").assertIsNotEnabled()
    }

    @Test fun cancellationNeverSendsReply() {
        val current = mutableStateOf(allowed.copy(pendingReply = ReplyDraft("draft", notification.id, 1, app.label, notification.title, "Resposta privada", "local", Long.MAX_VALUE)))
        var sends = 0
        var cancels = 0
        compose.setContent { AuronTheme {
            AutomationScreen(current.value, listOf(app), {}, {}, {}, {}, { _, _ -> }, { sends++ }, { cancels++; current.value = allowed }, {})
        } }
        compose.onNodeWithTag("automation_send_cancel").performClick()
        assertEquals(1, cancels)
        assertEquals(0, sends)
        compose.onNodeWithTag("automation_send_confirm").assertDoesNotExist()
    }

    @Test fun profileChangeRemovesPrivateReplyEditor() {
        val current = mutableStateOf(allowed)
        compose.setContent { AuronTheme {
            AutomationScreen(current.value, listOf(app), {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithTag("screen_automation").performScrollToNode(hasTestTag("automation_reply_message"))
        compose.onNodeWithTag("automation_reply_message").performClick()
        compose.onNodeWithTag("automation_reply_text").performTextInput("Rascunho privado")
        compose.runOnIdle { current.value = AutomationState(profileId = "other-account") }
        compose.onNodeWithTag("automation_reply_text").assertDoesNotExist()
        compose.onNodeWithText("Rascunho privado").assertDoesNotExist()
    }

    @Test fun explicitAppOpenDoesNotGrantNotificationAccess() {
        var opened: LaunchableApp? = null
        var saves = 0
        compose.setContent { AuronTheme {
            AutomationScreen(AutomationState(profileId = "local"), listOf(app), { saves++ }, {}, { opened = it }, {}, { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithTag("screen_automation").performScrollToNode(hasTestTag("automation_open_com.whatsapp"))
        compose.onNodeWithTag("automation_open_com.whatsapp").performClick()
        assertEquals(app, opened)
        assertEquals(0, saves)
    }
}
