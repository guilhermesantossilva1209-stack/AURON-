package br.com.auron.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal class AuthFailure(val safeMessage: String, val invalidSession: Boolean = false) : IOException(safeMessage)

/** Direct Supabase Auth REST, no SDK, redirects, credentials in URL or response logging. */
internal class SupabaseAuthClient {
    private val connections = mutableSetOf<HttpsURLConnection>()
    @Volatile private var closed = false

    suspend fun post(config: ConnectionConfig, path: String, payload: JSONObject, bearer: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val requestContext = currentCoroutineContext()
        requestContext.ensureActive()
        check(!closed) { "Authentication client closed" }
        val connection = (URL("${config.supabaseUrl}/auth/v1/$path").openConnection() as HttpsURLConnection)
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
        } catch (error: SocketTimeoutException) {
            throw AuthFailure("O serviço de autenticação demorou para responder. Verifique a conexão e tente novamente.")
        } catch (error: IOException) {
            if (error is AuthFailure) throw error
            throw AuthFailure("Não foi possível conectar com segurança ao serviço de autenticação. Verifique a conexão e a URL configurada.")
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
        val message = when {
            status in 300..399 -> "O servidor tentou redirecionar a autenticação. Confira a URL HTTPS raiz do projeto."
            status == 429 || code.contains("rate_limit") -> "Muitas tentativas. Aguarde alguns minutos antes de tentar novamente."
            code == "email_not_confirmed" -> "Confirme seu e-mail antes de entrar. Verifique também a pasta de spam."
            code == "weak_password" -> "O servidor recusou a senha por segurança. Use uma senha mais longa e variada."
            code == "signup_disabled" -> "O cadastro de contas está desativado neste serviço."
            code == "user_already_exists" || code == "email_exists" -> "Não foi possível cadastrar este e-mail. Tente entrar ou recuperar a senha."
            code == "invalid_credentials" || status == 400 -> "Não foi possível autenticar. Verifique e-mail, senha e confirmação da conta."
            status == 401 || status == 403 -> "A sessão ou a configuração pública de autenticação não foi aceita. Entre novamente ou confira a conexão."
            status >= 500 -> "O serviço de autenticação está indisponível. Tente novamente mais tarde."
            else -> "O serviço não concluiu a solicitação. Confira os dados e tente novamente."
        }
        return AuthFailure(message, invalidSession = status in listOf(400, 401, 403))
    }
}
