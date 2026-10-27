package br.com.auron.ai

import br.com.auron.domain.AiRequest
import br.com.auron.domain.AppSettings
import br.com.auron.domain.ChatMessage
import br.com.auron.domain.MemoryRecord
import br.com.auron.domain.ProviderResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises Android JSON and the actual request pipeline with an in-memory HTTPS fixture.
 * These are not tests against a live model, Supabase account or public TLS certificate.
 */
class RemoteAiTransportTest {
    private val settings = AppSettings(useRemoteAi = true)
    private val fixtureToken = "auron_local_" + "a".repeat(43)

    @Test fun localModeUsesItsOwnCredentialAndOnlyTheAuthorizedContext() = runBlocking {
        var openedUrl: URL? = null
        val connection = FixtureConnection(200, """{"text":"Resposta da fixture"}""")
        val provider = RemoteAiProvider(
            "https://gateway.example/", AiAccessMode.LocalGateway,
            connectionFactory = { openedUrl = it; connection },
        ) { fixtureToken }

        val result = provider.reply(AiRequest(
            "Olá", settings,
            history = listOf(ChatMessage("test-message", "user", "PRIVATE_HISTORY", 0, "text")),
            memories = listOf(MemoryRecord("test-memory", "PRIVATE_TITLE", "PRIVATE_MEMORY", 0)),
        ))

        assertEquals(ProviderResult.Success("Resposta da fixture"), result)
        assertEquals("https://gateway.example/v1/chat", openedUrl.toString())
        assertEquals("POST", connection.requestMethod)
        assertEquals("AuronLocal $fixtureToken", connection.getRequestProperty("Authorization"))
        assertEquals("application/json; charset=utf-8", connection.getRequestProperty("Content-Type"))
        assertFalse(connection.instanceFollowRedirects)
        val payload = JSONObject(connection.sent.toString("UTF-8"))
        assertEquals("Olá", payload.getString("input"))
        assertEquals(0, payload.getJSONArray("history").length())
        assertEquals(0, payload.getJSONArray("memories").length())
        assertFalse(payload.toString().contains("PRIVATE_"))
        assertTrue(connection.wasDisconnected)
    }

    @Test fun authenticatedModeUsesTheSessionWithoutSendingItInTheBody() = runBlocking {
        val connection = FixtureConnection(200, """{"text":"Resposta da fixture"}""")
        val provider = RemoteAiProvider("https://gateway.example", connectionFactory = { connection }) {
            "fixture.session.signature"
        }
        assertTrue(provider.reply("Olá", settings) is ProviderResult.Success)
        assertEquals("Bearer fixture.session.signature", connection.getRequestProperty("Authorization"))
        assertFalse(connection.sent.toString("UTF-8").contains("fixture.session.signature"))
        assertTrue(connection.wasDisconnected)
    }

    @Test fun serverFailuresExplainTheRequiredActionWithoutReflectingRemoteContent() = runBlocking {
        val cases = listOf(
            401 to "sessão", 403 to "OWNER_USER_ID", 404 to "POST /v1/chat",
            405 to "POST /v1/chat", 302 to "redireciona", 307 to "redireciona",
            429 to "Limite", 503 to "modelo",
        )
        for ((status, expected) in cases) {
            val connection = FixtureConnection(status, "PRIVATE_SERVER_DETAILS")
            val provider = RemoteAiProvider("https://gateway.example", connectionFactory = { connection }) {
                "fixture.session.signature"
            }
            val result = provider.reply("Olá", settings) as ProviderResult.Unavailable
            assertTrue("HTTP $status: ${result.reason}", result.reason.contains(expected))
            assertFalse(result.reason.contains("PRIVATE_SERVER_DETAILS"))
            assertFalse(connection.inputWasRead)
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.wasDisconnected)
        }
    }

    @Test fun invalidOrOversizedResponsesAreNeverAcceptedAsModelAnswers() = runBlocking {
        val payloads = listOf(
            """{"text":{"value":"not a string"}}""",
            """{"text":42}""", """{"text":null}""", """{"text":""}""",
            "<html>not the gateway</html>",
            """{"text":"${"a".repeat(16_001)}"}""",
            """{"text":"${"a".repeat(65_537)}"}""",
        )
        for (payload in payloads) {
            val connection = FixtureConnection(200, payload)
            val provider = RemoteAiProvider("https://gateway.example", connectionFactory = { connection }) {
                "fixture.session.signature"
            }
            assertTrue(provider.reply("Olá", settings) is ProviderResult.Unavailable)
            assertTrue(connection.wasDisconnected)
        }
    }

    @Test fun dnsTlsAndTimeoutFailuresAreDistinctAndDoNotExposeExceptionDetails() = runBlocking {
        val failures = listOf(
            UnknownHostException("PRIVATE_EXCEPTION") to "DNS",
            SSLHandshakeException("PRIVATE_EXCEPTION") to "certificado",
            SocketTimeoutException("PRIVATE_EXCEPTION") to "demorou",
        )
        for ((failure, expected) in failures) {
            val connection = FixtureConnection(200, "", failure)
            val provider = RemoteAiProvider("https://gateway.example", connectionFactory = { connection }) {
                "fixture.session.signature"
            }
            val result = provider.reply("Olá", settings) as ProviderResult.Unavailable
            assertTrue(result.reason.contains(expected))
            assertFalse(result.reason.contains("PRIVATE_EXCEPTION"))
            assertTrue(connection.wasDisconnected)
        }
    }

    private class FixtureConnection(
        private val status: Int,
        private val payload: String,
        private val failure: IOException? = null,
    ) : HttpsURLConnection(URL("https://gateway.example/v1/chat")) {
        val sent = ByteArrayOutputStream()
        var wasDisconnected = false
        var inputWasRead = false
        override fun getOutputStream(): OutputStream {
            failure?.let { throw it }
            return sent
        }
        override fun getInputStream(): InputStream {
            inputWasRead = true
            return ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))
        }
        override fun getResponseCode(): Int = status
        override fun connect() = Unit
        override fun disconnect() { wasDisconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getCipherSuite(): String = "TEST_FIXTURE"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
