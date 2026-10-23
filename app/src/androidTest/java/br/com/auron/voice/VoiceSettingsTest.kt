package br.com.auron.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.auron.domain.AppSettings
import br.com.auron.ui.VoiceSettings
import br.com.auron.ui.theme.AuronTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI boundary only: this test does not replace playback with a native TTS engine. */
class VoiceSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun auditionUsesSelectedDraftWithoutEnablingAutomaticRepliesOrSaving() {
        val voice = InstalledVoice("pt-br-offline", "test.engine", "pt", "BR", false, true, 300, 300)
        val draft = mutableStateOf(AppSettings(speechPitch = 0.9f, speechRate = 1.1f))
        var preview: AppSettings? = null
        compose.setContent {
            AuronTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    VoiceSettings(draft.value, VoiceState(voiceOptions = listOf(voice), ttsAvailable = true),
                        { draft.value = it }, { preview = it }, {}, {})
                }
            }
        }
        compose.onNodeWithTag("voice_choose").performClick()
        compose.onNodeWithText("Voz 1").performClick()
        compose.onNodeWithTag("voice_preview").performScrollTo().performClick()
        compose.runOnIdle {
            val requested = requireNotNull(preview)
            assertEquals(voice.name, requested.speechVoiceName)
            assertEquals(voice.engine, requested.speechVoiceEngine)
            assertEquals(0.9f, requested.speechPitch, 0.001f)
            assertEquals(1.1f, requested.speechRate, 0.001f)
            assertFalse(requested.speakReplies)
        }
    }

    @Test fun missingTtsPreventsAuditionAndExplainsHowToInstallOfflineData() {
        compose.setContent {
            AuronTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    VoiceSettings(AppSettings(), VoiceState(ttsAvailable = false), {},
                        { fail("No voice must be auditioned without a ready engine") }, {}, {})
                }
            }
        }
        compose.onNodeWithTag("voice_preview").assertIsNotEnabled()
        compose.onNodeWithText("Nenhuma voz offline pt-BR disponível.", substring = true).assertExists()
    }
}
