package br.com.auron.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.security.cert.Certificate
import java.util.ArrayDeque
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real manager, protocol serialization and Keystore; fixture transport, never a live account. */
@RunWith(AndroidJUnit4::class)
class AuthFlowTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var store: EncryptedSessionStore
    private lateinit var manager: AuthManager
    private val transport = FixtureTransport()
    private val config = ConnectionConfig("https://fixture.supabase.co", "sb_publishable_fixtureNotARealKey12345")
    private val userId = "12345678-1234-1234-1234-123456789abc"
    private val accessToken = "access-token-fixture-not-real-12345678"
    private val password = " fixture-password-with-spaces "

    @Before fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        directory = File(app.cacheDir, "auth-flow-$id").apply { mkdirs() }
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                app.getSharedPreferences("$name.test.$id", mode)
        }
        store = EncryptedSessionStore(context, "br.com.auron.auth.flow.test.$id")
        manager = newManager()
        runBlocking { withTimeout(5000) { manager.state.first { it.ready } } }
    }

    @After fun clean() {
        manager.close()
        store.clear()
        context.getSharedPreferences("auron.connections.v1", Context.MODE_PRIVATE).edit().clear().commit()
        directory.deleteRecursively()
    }

    @Test fun loginPersistsRestoresAndLogsOutWithoutStoringThePassword() = runBlocking {
        manager.saveConfig(config)
        transport.respond(200, sessionResponse())
        manager.signIn(" owner@example.com ", password)
        assertNull(manager.state.value.error)
        assertFalse(manager.state.value.busy)
        assertEquals(userId, manager.state.value.session?.userId)
        assertFalse(manager.state.value.localMode)
        val request = transport.requests.single()
        assertEquals("https://fixture.supabase.co/auth/v1/token?grant_type=password", request.url.toString())
        assertEquals("POST", request.requestMethod)
        assertEquals(config.supabasePublishableKey, request.getRequestProperty("apikey"))
        assertNull(request.getRequestProperty("Authorization"))
        assertFalse(request.instanceFollowRedirects)
        assertEquals("owner@example.com", JSONObject(request.sent.toString("UTF-8")).getString("email"))
        assertEquals(password, JSONObject(request.sent.toString("UTF-8")).getString("password"))
        assertTrue(request.disconnected)
        assertFalse(File(directory, "auron-auth-session.v1").readText(Charsets.ISO_8859_1).contains(password))
        manager.close()
        manager = newManager()
        withTimeout(5000) { manager.state.first { it.ready } }
        assertEquals(userId, manager.state.value.session?.userId)
        assertEquals(accessToken, manager.accessToken())
        assertEquals(1, transport.requests.size) // Fresh restoration does not sign in again.
        transport.respond(204, "")
        manager.signOut()
        assertNull(manager.state.value.session)
        assertNull(store.read(config.supabaseUrl))
        assertEquals("/auth/v1/logout?scope=local", transport.requests.last().url.file)
        assertEquals("Bearer $accessToken", transport.requests.last().getRequestProperty("Authorization"))
    }

    @Test fun ownerEnrollmentRequiresAcknowledgementAndConfirmationBeforeLogin() = runBlocking {
        manager.saveConfig(config)
        manager.signUp("owner@example.com", password, ownerPolicyConfirmed = false)
        assertTrue(manager.state.value.error!!.contains("Auth Hook"))
        assertTrue(transport.requests.isEmpty())
        transport.respond(200, """{"user":{"id":"$userId","email":"owner@example.com"}}""")
        manager.signUp("owner@example.com", password, ownerPolicyConfirmed = true)
        assertNull(manager.state.value.session)
        assertTrue(manager.state.value.notice!!.contains("confirmação"))
        assertEquals("/auth/v1/signup", transport.requests.last().url.path)
        transport.respond(400, """{"error_code":"email_not_confirmed"}""")
        manager.signIn("owner@example.com", password)
        assertNull(manager.state.value.session)
        assertTrue(manager.state.value.error!!.contains("Confirme seu e-mail"))
        transport.respond(200, sessionResponse())
        manager.signIn("owner@example.com", password)
        assertEquals(userId, manager.state.value.session?.userId)
        assertNull(manager.state.value.error)
        assertNull(manager.state.value.notice)
    }

    @Test fun missingSetupAndBadCredentialsCannotProduceASessionAndLocalModeStillOpens() = runBlocking {
        manager.signIn("owner@example.com", password)
        assertTrue(manager.state.value.error!!.contains("Configurar conexões"))
        assertTrue(transport.requests.isEmpty())
        manager.saveConfig(config)
        transport.respond(400, """{"error_code":"invalid_credentials","msg":"private response must not be shown"}""")
        manager.signIn("owner@example.com", password)
        assertNull(manager.state.value.session)
        assertTrue(manager.state.value.error!!.contains("E-mail ou senha"))
        assertFalse(manager.state.value.error!!.contains("private response"))
        manager.enterLocalMode()
        withTimeout(5000) { manager.state.first { it.localMode && !it.busy } }
        assertNull(manager.state.value.session)
        assertNull(manager.state.value.error)
    }

    @Test fun recoveryAndServerFailuresKeepExplicitStateWithoutClaimingDelivery() = runBlocking {
        manager.saveConfig(config)
        transport.respond(429, """{"error_code":"over_email_send_rate_limit"}""")
        manager.requestPasswordReset("owner@example.com")
        assertFalse(manager.state.value.passwordRecovery)
        assertTrue(manager.state.value.error!!.contains("Aguarde"))
        transport.respond(200, "{}")
        manager.requestPasswordReset("owner@example.com")
        assertTrue(manager.state.value.passwordRecovery)
        assertTrue(manager.state.value.notice!!.contains("Se houver"))
        val request = transport.requests.last()
        assertEquals("/auth/v1/recover", request.url.path)
        assertEquals(setOf("email"), JSONObject(request.sent.toString("UTF-8")).keys().asSequence().toSet())
        assertNull(manager.state.value.session)
    }

    @Test fun malformedSessionNeverUnlocksTheAccount() = runBlocking {
        manager.saveConfig(config)
        transport.respond(200, """{"access_token":"$accessToken","user":{"id":"$userId"}}""")
        manager.signIn("owner@example.com", password)
        assertNull(manager.state.value.session)
        assertTrue(manager.state.value.error!!.contains("sessão válida"))
        assertFalse(manager.state.value.busy)
        assertNull(store.read(config.supabaseUrl))
    }

    @Test fun configurationFailureDuringRefreshPreservesSessionButRevocationClosesIt() = runBlocking {
        manager.saveConfig(config)
        transport.respond(200, sessionResponse(expiresIn = 30))
        manager.signIn("owner@example.com", password)
        transport.respond(401, """{"message":"Invalid API key"}""")
        assertNull(manager.accessToken())
        assertEquals(userId, manager.state.value.session?.userId)
        assertTrue(manager.state.value.error!!.contains("mesmo projeto"))
        assertNotNull(store.read(config.supabaseUrl))
        // Restart clears the retry delay while preserving the encrypted session.
        manager.close()
        manager = newManager()
        withTimeout(5000) { manager.state.first { it.ready } }
        transport.respond(400, """{"error_code":"refresh_token_not_found"}""")
        assertNull(manager.accessToken())
        assertNull(manager.state.value.session)
        assertNull(store.read(config.supabaseUrl))
    }

    @Test fun networkFailuresAreSafeAndDoNotPreventLocalAccessOrALaterLogin() = runBlocking {
        manager.saveConfig(config)
        listOf(
            UnknownHostException("private hostname") to "endereço do Supabase",
            SSLHandshakeException("private certificate details") to "conexão HTTPS",
            SocketTimeoutException("private request details") to "demorou",
        ).forEach { (failure, expected) ->
            transport.failOpening(failure)
            manager.signIn("owner@example.com", password)
            assertTrue(manager.state.value.error.orEmpty().contains(expected))
            assertFalse(manager.state.value.error.orEmpty().contains("private"))
            assertFalse(manager.state.value.busy)
            assertNull(manager.state.value.session)
        }
        assertTrue(transport.requests.isEmpty())
        manager.enterLocalMode()
        withTimeout(5000) { manager.state.first { it.localMode && !it.busy } }
        manager.showLogin()
        withTimeout(5000) { manager.state.first { !it.localMode && !it.busy } }
        transport.respond(200, sessionResponse())
        manager.signIn("owner@example.com", password)
        assertEquals(userId, manager.state.value.session?.userId)
        assertNull(manager.state.value.error)
    }

    private fun newManager() = AuthManager(context, SupabaseAuthClient(transport::open), store)
    private fun sessionResponse(expiresIn: Int = 3600) = JSONObject().put("access_token", accessToken)
        .put("refresh_token", "refresh-fixture-only").put("expires_in", expiresIn)
        .put("user", JSONObject().put("id", userId).put("email", "owner@example.com")).toString()

    private class FixtureTransport {
        val requests = mutableListOf<FixtureConnection>()
        private val responses = ArrayDeque<Pair<Int, String>>()
        private var openingFailure: IOException? = null
        fun respond(status: Int, body: String) { responses.add(status to body) }
        fun failOpening(error: IOException) { openingFailure = error }
        fun open(url: URL): HttpsURLConnection {
            check(url.protocol == "https" && url.host == "fixture.supabase.co")
            openingFailure?.let { openingFailure = null; throw it }
            val (status, body) = responses.removeFirst()
            return FixtureConnection(url, status, body).also { requests.add(it) }
        }
    }
    private class FixtureConnection(url: URL, private val status: Int, private val body: String) : HttpsURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getErrorStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getOutputStream() = sent
        override fun getCipherSuite() = "TLS_TEST_FIXTURE"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
