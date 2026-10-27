package br.com.auron.auth

import org.junit.Assert.*
import org.junit.Test

class AuthErrorsTest {
    @Test fun invalidCredentialsGuideAnUnregisteredOwnerWithoutClaimingAccountExists() {
        val result = AuthErrors.fromServer(400, "invalid_credentials")
        assertTrue(result.message.contains("Primeiro acesso do proprietário"))
        assertFalse(result.invalidSession)
    }
    @Test fun setupAndEmailErrorsHaveSpecificActions() {
        assertTrue(AuthErrors.fromServer(401, "", "Invalid API key").message.contains("mesmo projeto"))
        assertTrue(AuthErrors.fromServer(422, "signup_disabled").message.contains("Hook"))
        assertTrue(AuthErrors.fromServer(400, "email_provider_disabled").message.contains("Providers"))
        assertTrue(AuthErrors.fromServer(400, "email_not_confirmed").message.contains("Confirme seu e-mail"))
        assertTrue(AuthErrors.fromServer(400, "email_address_not_authorized").message.contains("SMTP"))
        assertTrue(AuthErrors.fromServer(500, "hook_timeout").message.contains("não desative"))
    }
    @Test fun configurationAndTransientErrorsDoNotDestroyAnExistingSession() {
        listOf(401 to "", 403 to "hook_error", 400 to "email_provider_disabled", 429 to "over_request_rate_limit", 500 to "unexpected_failure", 504 to "request_timeout")
            .forEach { (status, code) -> assertFalse(AuthErrors.fromServer(status, code).invalidSession) }
        assertFalse(AuthErrors.fromServer(401, "", "Invalid API key").invalidSession)
    }
    @Test fun knownRevocationAndExpiryErrorsInvalidateTheSession() {
        listOf("refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired", "bad_jwt", "user_banned")
            .forEach { code -> assertTrue(code, AuthErrors.fromServer(400, code).invalidSession) }
    }
    @Test fun noUnknownServerTextOrCodeIsReflectedIntoTheInterface() {
        val secret = "token-fixture-PRIVATE owner@example.com"
        val result = AuthErrors.fromServer(500, secret, secret)
        assertFalse(result.message.contains(secret))
        assertFalse(result.message.contains("owner@example.com"))
    }
}
