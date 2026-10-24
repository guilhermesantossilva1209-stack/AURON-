package br.com.auron.ai

import org.junit.Assert.*
import org.junit.Test

class AiAccessTest {
    private val token = "auron_local_" + "a".repeat(43)

    @Test fun `local token is explicit and cannot be sent as a Supabase bearer`() {
        assertEquals("AuronLocal $token", AiAccess.authorization(AiAccessMode.LocalGateway, token))
        assertNull(AiAccess.authorization(AiAccessMode.SupabaseSession, token))
        assertNull(AiAccess.authorization(AiAccessMode.LocalGateway, "session.access.token"))
        assertEquals("Bearer session.access.token", AiAccess.authorization(AiAccessMode.SupabaseSession, "session.access.token"))
    }

    @Test fun `missing malformed secret and header injection values cannot authorize local inference`() {
        for (value in listOf(null, "", "auron_local_short", "sb_secret_key", "sk-provider-key", "$token\r\nX-Extra: true", token + "a".repeat(129))) {
            assertNull(AiAccess.authorization(AiAccessMode.LocalGateway, value))
        }
        assertThrows(IllegalArgumentException::class.java) { AiAccess.requireLocalToken("sb_secret_key") }
    }

    @Test fun `session authorization rejects line breaks and overlong credentials`() {
        assertNull(AiAccess.authorization(AiAccessMode.SupabaseSession, "token\r\nX-Extra: true"))
        assertNull(AiAccess.authorization(AiAccessMode.SupabaseSession, "a".repeat(8_193)))
        assertNull(AiAccess.authorization(AiAccessMode.SupabaseSession, null))
    }
}
