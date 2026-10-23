package br.com.auron.ai

import br.com.auron.domain.AppSettings
import br.com.auron.domain.ProviderResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RemoteAiProviderTest {
    @Test fun `remote opt out does not request a credential`() = runBlocking {
        var tokenCalls = 0
        val provider = RemoteAiProvider("https://auron.example") { tokenCalls++; "token" }
        assertTrue(provider.reply("Olá", AppSettings()) is ProviderResult.Unavailable)
        assertEquals(0, tokenCalls)
    }

    @Test fun `insecure origins credentials paths and fragments are rejected before auth`() = runBlocking {
        var tokenCalls = 0
        listOf("http://auron.example", "https://user:pass@auron.example", "https://auron.example/path", "https://auron.example/#fragment")
            .forEach { url ->
                val provider = RemoteAiProvider(url) { tokenCalls++; "token" }
                assertTrue(provider.reply("Olá", AppSettings(useRemoteAi = true)) is ProviderResult.Unavailable)
            }
        assertEquals(0, tokenCalls)
    }

    @Test fun `blank oversized and missing session inputs stop before network`() = runBlocking {
        var tokenCalls = 0
        val provider = RemoteAiProvider("https://auron.example") { tokenCalls++; null }
        assertTrue(provider.reply(" ", AppSettings(useRemoteAi = true)) is ProviderResult.Unavailable)
        assertTrue(provider.reply("a".repeat(4_001), AppSettings(useRemoteAi = true)) is ProviderResult.Unavailable)
        assertEquals(0, tokenCalls)
        assertTrue(provider.reply("Olá", AppSettings(useRemoteAi = true)) is ProviderResult.Unavailable)
        assertEquals(1, tokenCalls)
    }

    @Test fun `auth failure does not reveal exception details`() = runBlocking {
        val provider = RemoteAiProvider("https://auron.example") { error("PRIVATE_TOKEN_DEBUG") }
        val result = provider.reply("Olá", AppSettings(useRemoteAi = true)) as ProviderResult.Unavailable
        assertTrue(!result.reason.contains("PRIVATE_TOKEN_DEBUG"))
    }

    @Test fun `auth cancellation propagates instead of becoming a fabricated answer`() = runBlocking {
        val provider = RemoteAiProvider("https://auron.example") { throw CancellationException("cancelled") }
        try {
            provider.reply("Olá", AppSettings(useRemoteAi = true))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { /* Caller owns cancellation and profile changes. */ }
    }
}
