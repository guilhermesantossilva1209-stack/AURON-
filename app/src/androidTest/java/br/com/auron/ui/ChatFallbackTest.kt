package br.com.auron.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import br.com.auron.MainActivity
import br.com.auron.auth.ConnectionConfig
import br.com.auron.domain.AppSettings
import br.com.auron.domain.ChatMessage
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real Activity/ViewModel/Room/DataStore on a disposable emulator. No live network or account. */
class ChatFallbackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: AuronViewModel
    private var originalConfig: ConnectionConfig? = null
    private var originalSettings: AppSettings? = null

    @Before fun prepareLocalProfile() {
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[AuronViewModel::class.java] }
        compose.waitUntil(10_000) { model.auth.state.value.ready && !model.auth.state.value.busy }
        check(model.auth.state.value.session == null) { "Use a disposable emulator without a personal account." }
        originalConfig = model.auth.config.value
        compose.runOnIdle { model.auth.enterLocalMode() }
        compose.waitUntil(10_000) { model.state.value.ready && model.state.value.profileId == "local" && !model.auth.state.value.busy }
        originalSettings = model.state.value.settings
        compose.waitUntil(10_000) {
            if (compose.onAllNodesWithTag("intro_skip").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("intro_skip").performClick()
            }
            compose.onAllNodesWithTag("nav_chat").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav_chat").performScrollTo().performClick()
        setRemote(true)
    }

    @After fun restorePreferences() {
        originalConfig?.let { runBlocking { model.auth.saveConfig(it) } }
        originalSettings?.let { saved ->
            compose.runOnIdle { model.saveSettings(saved) }
            compose.waitUntil(10_000) { model.state.value.settings == saved && !model.state.value.busy }
        }
        // Keep identifiable test messages; never clear an existing conversation/database.
    }

    @Test fun missingServerFallsBackLocallyAndLocalCommandsRemainAvailable() {
        runBlocking { model.auth.saveConfig(model.auth.config.value.copy(aiBaseUrl = "")) }
        val answer = submit("Olá teste ${UUID.randomUUID()}")
        assertEquals("demo", answer.source)
        assertTrue(answer.text.contains("Nenhum servidor de IA foi configurado"))
        assertTrue(answer.text.contains("Demonstração local"))
        assertEquals("Modo local · IA online não configurada", model.state.value.aiStatus)
        assertTrue(model.state.value.settings.useRemoteAi) // No silent change to consent.

        val command = submit("Mostre minha rotina.")
        assertEquals("system", command.source)
        assertFalse(command.text.contains("IA online indisponível"))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("routine_morning").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun missingLocalCredentialDoesNotRequireLoginAndRemoteOptOutStillWorks() {
        runBlocking { model.auth.saveConfig(model.auth.config.value.copy(aiBaseUrl = "https://gateway.example")) }
        val answer = submit("Olá teste ${UUID.randomUUID()}")
        assertEquals("demo", answer.source)
        assertTrue(answer.text.contains("token pessoal"))
        assertTrue(answer.text.contains("Demonstração local"))
        assertEquals("Modo local · IA online indisponível", model.state.value.aiStatus)
        assertTrue(model.auth.state.value.localMode)
        assertNull(model.auth.state.value.session)

        setRemote(false)
        val offline = submit("Olá teste ${UUID.randomUUID()}")
        assertEquals("demo", offline.source)
        assertTrue(offline.text.startsWith("Demonstração local"))
        assertFalse(offline.text.contains("IA online indisponível"))
    }

    private fun setRemote(enabled: Boolean) {
        compose.runOnIdle { model.saveSettings(model.state.value.settings.copy(useRemoteAi = enabled, speakReplies = false)) }
        compose.waitUntil(10_000) {
            model.state.value.settings.useRemoteAi == enabled && !model.state.value.settings.speakReplies && !model.state.value.busy
        }
    }

    private fun submit(text: String): ChatMessage {
        val before = model.state.value.messages.map { it.id }.toSet()
        compose.onNodeWithTag("chat_input").performTextInput(text)
        closeSoftKeyboard()
        compose.onNodeWithTag("chat_send").performClick()
        compose.waitUntil(10_000) {
            !model.state.value.busy && model.state.value.messages.any { it.role == "assistant" && it.id !in before }
        }
        return model.state.value.messages.last { it.role == "assistant" && it.id !in before }
    }
}
