package br.com.auron.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

internal class AuthFailure(val safeMessage: String, val invalidSession: Boolean = false) : IOException(safeMessage)

/** Direct Supabase Auth REST, no SDK, redirects, credentials in URL or response logging. */
internal class SupabaseAuthClient(
    private val openConnection: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
) {
    private val connections = mutableSetOf<HttpsURLConnection>()
    @Volatile private var closed = false

    suspend fun post(config: ConnectionConfig, path: String, payload: JSONObject, bearer: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val requestContext = currentCoroutineContext()
        requestContext.ensureActive()
        check(!closed) { "Authentication client closed" }
        val connection = try {
            openConnection(URL("${config.supabaseUrl}/auth/v1/$path"))
        } catch (error: IOException) {
            throw connectionError(error)
        }
        synchronized(connections) {
            if (closed) { connection.disconnect(); throw AuthFailure("O serviço de autenticação foi encerrado.") }
            connections.add(connection)
        }
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("apikey", config.supabasePublishableKey)
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.doOutput = true
            val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(requestBytes.size)
            connection.outputStream.use { it.write(requestBytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                val deadline = System.nanoTime() + 20_000_000_000L
                while (true) {
                    requestContext.ensureActive()
                    if (System.nanoTime() > deadline) throw SocketTimeoutException()
                    val count = it.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > 131_072) throw AuthFailure("O servidor retornou uma resposta acima do limite permitido.")
                    output.write(buffer, 0, count)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }.orEmpty()
            currentCoroutineContext().ensureActive()
            val json = if (response.isBlank()) JSONObject() else runCatching { JSONObject(response) }.getOrNull()
            if (status !in 200..299) throw serverError(status, json)
            json ?: throw AuthFailure("O servidor de autenticação retornou uma resposta inválida.")
        } catch (error: IOException) {
            throw connectionError(error)
        } finally {
            synchronized(connections) { connections.remove(connection) }
            connection.disconnect()
        }
    }

    fun session(json: JSONObject): AuthSession? {
        if (json.optString("access_token").isBlank()) return null
        try {
            val user = json.getJSONObject("user")
            val now = System.currentTimeMillis() / 1000L
            return AuthSession(
                userId = user.getString("id"), email = user.optString("email"),
                accessToken = json.getString("access_token"), refreshToken = json.getString("refresh_token"),
                expiresAt = if (json.has("expires_at")) json.getLong("expires_at") else now + json.getLong("expires_in"),
            ).also {
                require(it.userId.matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")))
                require(it.email.length <= 320 && it.accessToken.length in 20..32768 && it.refreshToken.length in 1..8192 && it.expiresAt > now)
            }
        } catch (_: Exception) {
            throw AuthFailure("A resposta de autenticação não contém uma sessão válida.")
        }
    }

    fun close() {
        synchronized(connections) {
            closed = true
            connections.toList().forEach { it.disconnect() }
            connections.clear()
        }
    }

    private fun serverError(status: Int, body: JSONObject?): AuthFailure {
        val code = body?.optString("error_code").orEmpty().ifBlank { body?.optString("code").orEmpty() }
        val hint = body?.optString("msg").orEmpty().ifBlank {
            body?.optString("message").orEmpty().ifBlank { body?.optString("error_description").orEmpty() }
        }
        val result = AuthErrors.fromServer(status, code, hint)
        return AuthFailure(result.message, result.invalidSession)
    }

    private fun connectionError(error: IOException): AuthFailure = when (error) {
        is AuthFailure -> error
        is SocketTimeoutException -> AuthFailure("O serviço de autenticação demorou para responder. Verifique a conexão e tente novamente.")
        is UnknownHostException -> AuthFailure("O endereço do Supabase não foi encontrado. Confira a internet e a Project URL em Configurar conexões.")
        is SSLException -> AuthFailure("Não foi possível validar a conexão HTTPS com o Supabase. Confira a data do aparelho e a Project URL; não desative a segurança do certificado.")
        else -> AuthFailure("Não foi possível conectar com segurança ao serviço de autenticação. Verifique a conexão e a URL configurada.")
    }
}
