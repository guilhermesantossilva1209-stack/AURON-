package br.com.auron.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPoliciesTest {
    private val publicKey = "sb_publishable_thisIsPublicConfiguration123"

    @Test fun initialEnrollmentRequiresExplicitServerPolicyAcknowledgement() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthPolicies.requireControlledRegistrationConfirmed(false)
        }
        AuthPolicies.requireControlledRegistrationConfirmed(true)
        // Acceptance here is consent only. Server Hook/RLS authorization has
        // separate integration tests and cannot be proved by this pure check.
    }

    @Test fun emptyConfigurationPreservesLocalModeOption() {
        assertEquals(ConnectionConfig(), AuthPolicies.normalize(ConnectionConfig()))
    }

    @Test fun trimsPublicConfigurationAndTrailingSlash() {
        assertEquals(ConnectionConfig("https://project.supabase.co", publicKey, "https://ai.example.com"),
            AuthPolicies.normalize(ConnectionConfig(" https://project.supabase.co/ ", " $publicKey ", " https://ai.example.com/ ")))
    }

    @Test fun partialSupabaseConfigurationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { AuthPolicies.normalize(ConnectionConfig(supabaseUrl = "https://project.supabase.co")) }
        assertThrows(IllegalArgumentException::class.java) { AuthPolicies.normalize(ConnectionConfig(supabasePublishableKey = publicKey)) }
    }

    @Test fun urlsRejectInsecureSchemesCredentialsAndDataInQueryOrFragment() {
        listOf("http://example.com", "https://user:password@example.com", "https://example.com?token=private",
            "https://example.com#secret", "https:///missing-host", "https://example.com:70000", "https://exam ple.com")
            .forEach { value -> assertThrows(value, IllegalArgumentException::class.java) { AuthPolicies.requireHttps(value) } }
    }

    @Test fun supabaseAndAiBothRequireRootUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthPolicies.normalize(ConnectionConfig("https://project.supabase.co/auth/v1", publicKey))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthPolicies.normalize(ConnectionConfig(aiBaseUrl = "https://example.com/auron/"))
        }
    }

    @Test fun publicPublishableAndLegacyAnonKeysAreAccepted() {
        AuthPolicies.requirePublicKey(publicKey)
        AuthPolicies.requirePublicKey(jwt("""{"role":"anon","iss":"supabase"}"""))
    }

    @Test fun privilegedAndProviderKeysAreRejected() {
        listOf("sb_secret_notForAndroid123456789", "sk-provider-secret-not-for-android",
            jwt("""{"role":"service_role"}"""), jwt("""{"role":"authenticated"}"""),
            jwt("""{"role":"anon","role":"service_role"}"""))
            .forEach { key -> assertThrows(IllegalArgumentException::class.java) { AuthPolicies.requirePublicKey(key) } }
    }

    @Test fun malformedJwtAndWhitespaceKeysAreRejected() {
        listOf("not-a-jwt-key-more-than-20", "header.@@@.signature123456", "sb_publishable_has a space")
            .forEach { key -> assertThrows(IllegalArgumentException::class.java) { AuthPolicies.requirePublicKey(key) } }
    }

    @Test fun emailIsTrimmedWithoutChangingCaseOrPassword() {
        assertEquals("Name@example.com", AuthPolicies.email(" Name@example.com "))
        assertThrows(IllegalArgumentException::class.java) { AuthPolicies.email("name at example.com") }
        AuthPolicies.password(" leading and trailing ", registering = true)
    }

    @Test fun loginAcceptsExistingShortPasswordButRegistrationEnforcesMinimum() {
        AuthPolicies.password("short", registering = false)
        assertThrows(IllegalArgumentException::class.java) { AuthPolicies.password("short", registering = true) }
        assertThrows(IllegalArgumentException::class.java) { AuthPolicies.password("", registering = false) }
        assertThrows(IllegalArgumentException::class.java) { AuthPolicies.password("x".repeat(4097), registering = false) }
    }

    @Test fun refreshUsesEpochSecondsAndSixtySecondWindow() {
        val now = 1_800_000_000L
        assertTrue(AuthPolicies.needsRefresh(now - 1, now))
        assertTrue(AuthPolicies.needsRefresh(now + 60, now))
        assertFalse(AuthPolicies.needsRefresh(now + 61, now))
        assertFalse(AuthPolicies.needsRefresh(now + 3600, now))
    }

    private fun jwt(payload: String): String {
        fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        return "${encode("""{"alg":"HS256","typ":"JWT"}""")}.${encode(payload)}.fixtureSignatureForPolicyOnly"
    }
}
