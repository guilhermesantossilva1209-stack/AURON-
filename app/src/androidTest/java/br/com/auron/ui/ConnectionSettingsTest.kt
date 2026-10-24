package br.com.auron.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.espresso.Espresso.closeSoftKeyboard
import br.com.auron.auth.ConnectionConfig
import br.com.auron.ui.theme.AuronTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI validation only: callbacks neither contact a server nor prove token storage. */
class ConnectionSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val endpoint = "https://gateway.example.com"
    private val fixtureToken = "auron_local_" + "a".repeat(43)

    @Test fun localCredentialIsMaskedAndPassedOnlyToItsSeparateCallback() {
        var saved: Pair<String, String>? = null
        compose.setContent { AuronTheme { ConnectionSettings(
            config = ConnectionConfig(aiBaseUrl = endpoint), busy = false,
            onSave = { fail("Credential must not be placed in public connection configuration") },
            onSaveLocalAiAccess = { url, token -> saved = url to token },
        ) } }
        compose.onNodeWithTag("connection_local_ai_token").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Password, Unit))
            .performTextInput(fixtureToken)
        closeSoftKeyboard()
        assertNull(saved)
        compose.onNodeWithTag("connection_local_ai_save").performScrollTo().performClick()
        assertEquals(endpoint to fixtureToken, saved)
        compose.onNodeWithTag("connection_local_ai_token").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
    }

    @Test fun changingEndpointRequiresSavingItBeforeAssociatingAnyCredential() {
        compose.setContent { AuronTheme { ConnectionSettings(
            config = ConnectionConfig(aiBaseUrl = endpoint), busy = false, onSave = {},
            onSaveLocalAiAccess = { _, _ -> fail("Unsaved endpoint must not receive a credential") },
        ) } }
        compose.onNodeWithTag("connection_ai_url").performScrollTo()
            .performTextReplacement("https://different.example.com")
        compose.onNodeWithTag("connection_local_ai_token").performScrollTo().performTextInput(fixtureToken)
        closeSoftKeyboard()
        compose.onNodeWithTag("connection_local_ai_save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("connection_local_ai_save_url_first").performScrollTo().assertIsDisplayed()
    }

    @Test fun providerKeysCannotBeSavedAsLocalGatewayCredentials() {
        compose.setContent { AuronTheme { ConnectionSettings(
            config = ConnectionConfig(aiBaseUrl = endpoint), busy = false, onSave = {},
            onSaveLocalAiAccess = { _, _ -> fail("Provider key must be rejected") },
        ) } }
        compose.onNodeWithTag("connection_local_ai_token").performScrollTo()
            .performTextInput("sk-provider-fixture-not-a-real-secret")
        closeSoftKeyboard()
        compose.onNodeWithTag("connection_local_ai_save").performScrollTo().performClick()
        compose.onNodeWithTag("connection_local_ai_validation").performScrollTo().assertIsDisplayed()
    }

    @Test fun removingPersonalAccessUsesOnlyTheCredentialRemovalCallback() {
        var removed = false
        compose.setContent { AuronTheme { ConnectionSettings(
            config = ConnectionConfig(aiBaseUrl = endpoint), busy = false,
            onSave = { fail("Removing personal access must not change public connection settings") },
            localAiConfigured = true,
            onClearLocalAiAccess = { removed = true },
        ) } }
        compose.onNodeWithTag("connection_local_ai_clear").performScrollTo().assertIsEnabled().performClick()
        assertTrue(removed)
    }
}
