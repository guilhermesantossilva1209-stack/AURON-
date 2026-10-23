package br.com.auron.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import br.com.auron.auth.AuthState
import br.com.auron.ui.theme.AuronTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI boundary only. Successful callbacks do not prove authentication with Supabase. */
class AuthScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unconfiguredServiceCannotPretendToSignIn() {
        var local = false
        compose.setContent { AuronTheme { AuthScreen(AuthState(ready = true), false,
            { _, _ -> fail("Must not login") }, { _, _ -> fail("Must not register") },
            { fail("Must not reset") }, { local = true }, {}) } }
        compose.onNodeWithTag("auth_submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("auth_local").performScrollTo().performClick()
        assertTrue(local)
    }
    @Test fun registrationRequiresMatchingPasswordsAndExplicitConsent() {
        var registered: Pair<String, String>? = null
        compose.setContent { AuronTheme { AuthScreen(AuthState(ready = true), true,
            { _, _ -> }, { email, password -> registered = email to password }, {}, {}, {}) } }
        compose.onNodeWithText("Primeiro acesso do proprietário").performScrollTo().performClick()
        compose.onNodeWithTag("auth_email").performScrollTo().performTextInput("test@example.com")
        compose.onNodeWithTag("auth_password").performScrollTo().performTextInput("test-password-only")
        compose.onNodeWithTag("auth_password_confirmation").performScrollTo().performTextInput("wrong-password")
        closeSoftKeyboard()
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        assertNull(registered)
        compose.onNodeWithText("As senhas precisam ser iguais.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth_password_confirmation").performScrollTo().performTextReplacement("test-password-only")
        closeSoftKeyboard()
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        assertNull(registered)
        compose.onNodeWithText("Configure e confirme a proteção do primeiro cadastro no Supabase.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth_registration_consent").performScrollTo().performClick()
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        assertNull(registered)
        compose.onNodeWithTag("auth_owner_policy_confirmed").performScrollTo().performClick()
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        assertEquals("test@example.com" to "test-password-only", registered)
    }
    @Test fun recoveryRequestsOnlyTheEmail() {
        var requested: String? = null
        compose.setContent { AuronTheme { AuthScreen(AuthState(ready = true), true,
            { _, _ -> }, { _, _ -> }, { requested = it }, {}, {}) } }
        compose.onNodeWithText("Esqueci minha senha").performScrollTo().performClick()
        compose.onNodeWithTag("auth_password").assertDoesNotExist()
        compose.onNodeWithTag("auth_email").performScrollTo().performTextInput("test@example.com")
        closeSoftKeyboard()
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        assertEquals("test@example.com", requested)
    }
}
