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

    @Test fun configuredLoginValidatesAddressAndSubmitsFromKeyboardWithoutChangingPassword() {
        var submitted: Pair<String, String>? = null
        compose.setContent { AuronTheme { AuthScreen(AuthState(ready = true), true,
            { email, password -> submitted = email to password },
            { _, _ -> fail("Login must not register") },
            { fail("Login must not reset") }, {}, {}) } }

        compose.onNodeWithTag("auth_email").performScrollTo().performTextInput("invalid-address")
        compose.onNodeWithTag("auth_password").performScrollTo().performTextInput(" old-password ")
        compose.onNodeWithTag("auth_password").performImeAction()
        assertNull(submitted)
        closeSoftKeyboard()
        compose.onNodeWithText("Digite um e-mail válido.").performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag("auth_email").performScrollTo().performTextReplacement(" owner@example.com ")
        compose.onNodeWithTag("auth_email").performImeAction()
        compose.onNodeWithTag("auth_password").assertIsFocused().performImeAction()
        assertEquals("owner@example.com" to " old-password ", submitted)
    }

    @Test fun unconfiguredLoginAcceptsTouchAndTypingButCannotSubmitFromKeyboard() {
        var openedConnections = false
        compose.setContent { AuronTheme { AuthScreen(AuthState(ready = true), false,
            { _, _ -> fail("Unconfigured login must not send credentials") },
            { _, _ -> fail("Unconfigured registration must not send credentials") },
            { fail("Unconfigured recovery must not send an email") }, {},
            { openedConnections = true }) } }

        compose.onNodeWithTag("auth_email").performScrollTo().assertIsEnabled().performClick().assertIsFocused()
            .performTextInput("owner@example.com")
        compose.onNodeWithTag("auth_email").assertTextContains("owner@example.com").performImeAction()
        compose.onNodeWithTag("auth_password").assertIsFocused().performTextInput("owner-password-only")
        compose.onNodeWithTag("auth_password").performImeAction()
        closeSoftKeyboard()
        compose.onNodeWithTag("auth_submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("auth_configure_service").performScrollTo().assertIsEnabled().performClick()
        assertTrue(openedConnections)
    }

    @Test fun unconfiguredOwnerRegistrationAllowsAllFieldsWithoutSendingCredentials() {
        compose.setContent { AuronTheme { AuthScreen(AuthState(ready = true), false,
            { _, _ -> fail("Unconfigured login must not send credentials") },
            { _, _ -> fail("Unconfigured registration must not send credentials") },
            { fail("Unconfigured recovery must not send an email") }, {}, {}) } }
        compose.onNodeWithText("Primeiro acesso do proprietário").performScrollTo().performClick()
        compose.onNodeWithTag("auth_email").performScrollTo().performClick().assertIsFocused()
            .performTextInput("owner@example.com")
        compose.onNodeWithTag("auth_email").performImeAction()
        compose.onNodeWithTag("auth_password").assertIsFocused().performTextInput("owner-password-only")
        compose.onNodeWithTag("auth_password").performImeAction()
        compose.onNodeWithTag("auth_password_confirmation").assertIsFocused().performTextInput("owner-password-only")
        compose.onNodeWithTag("auth_password_confirmation").performImeAction()
        closeSoftKeyboard()
        compose.onNodeWithTag("auth_owner_policy_confirmed").performScrollTo().performClick()
        compose.onNodeWithTag("auth_registration_consent").performScrollTo().performClick()
        compose.onNodeWithTag("auth_submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("auth_configure_service").performScrollTo().assertIsDisplayed()
    }

    @Test fun unconfiguredRecoveryRemainsEditableWithoutSendingRequests() {
        compose.setContent { AuronTheme { AuthScreen(AuthState(ready = true), false,
            { _, _ -> fail("Must not login") }, { _, _ -> fail("Must not register") },
            { fail("Unconfigured recovery must not send an email") }, {}, {}) } }
        compose.onNodeWithText("Esqueci minha senha").performScrollTo().performClick()
        compose.onNodeWithTag("auth_email").performScrollTo().assertIsEnabled().performClick().assertIsFocused()
            .performTextInput("owner@example.com")
        compose.onNodeWithTag("auth_email").performImeAction()
        closeSoftKeyboard()
        compose.onNodeWithTag("auth_password").assertDoesNotExist()
        compose.onNodeWithTag("auth_submit").performScrollTo().assertIsNotEnabled()
    }

    @Test fun activeRequestPreventsDuplicateSubmissionsAndEditing() {
        compose.setContent { AuronTheme { AuthScreen(AuthState(ready = true, busy = true), true,
            { _, _ -> fail("Must not login again") }, { _, _ -> fail("Must not register") },
            { fail("Must not reset") }, {}, {}) } }
        compose.onNodeWithTag("auth_email").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("auth_password").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("auth_submit").performScrollTo().assertIsNotEnabled()
    }

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
