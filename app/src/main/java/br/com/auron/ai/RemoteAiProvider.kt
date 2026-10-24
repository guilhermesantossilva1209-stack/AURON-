package br.com.auron.ai

import br.com.auron.domain.AiProvider
import br.com.auron.domain.AiRequest
import br.com.auron.domain.AppSettings
import br.com.auron.domain.InputLimits
import br.com.auron.domain.ProviderResult
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/** HTTPS only; cancellation disconnects the request. Model output is always text. */
class RemoteAiProvider(
    val baseUrl: String,
    val accessMode: AiAccessMode = AiAccessMode.SupabaseSession,
    val tokenProvider: suspend () -> String?,
) : AiProvider {
    override val id = if (accessMode == AiAccessMode.LocalGateway) "personal-gateway-v1" else "authenticated-remote-v1"
    override val isDemo = false

    override suspend fun reply(input: String, settings: AppSettings): ProviderResult = reply(AiRequest(input, settings))

    override suspend fun reply(request: AiRequest): ProviderResult {
        if (!request.settings.useRemoteAi) return unavailable("A IA remota está desativada nas preferências.")
        if (request.input.isBlank() || request.input.length > InputLimits.MESSAGE) {
            return unavailable("Envie uma mensagem entre 1 e ${InputLimits.MESSAGE} caracteres.")
        }
        val endpoint = runCatching {
            val uri = URI(baseUrl.trim())
            require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null)
            require(uri.query == null && uri.fragment == null && uri.path.orEmpty() in listOf("", "/"))
            URI("https", null, uri.host, uri.port, "/v1/chat", null, null).toURL()
        }.getOrElse { return unavailable("Configure uma URL HTTPS válida para o servidor de IA, sem caminho ou credenciais.") }
        val token = try { tokenProvider() } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { return unavailable(accessError()) }
        val authorization = AiAccess.authorization(accessMode, token) ?: return unavailable(accessError())
        val context = AiContextBuilder.build(request)
        val body = JSONObject().apply {
            put("input", request.input)
            put("shareHistory", context.shareHistory)
            put("shareMemory", context.shareMemory)
            put("history", JSONArray().apply { context.history.forEach { put(JSONObject().put("role", it.role).put("text", it.text)) } })
            put("memories", JSONArray().apply { context.memories.forEach { put(JSONObject().put("title", it.title).put("content", it.content)) } })
            put("personality", JSONObject().apply {
                put("humor", request.settings.humor.coerceIn(0, 100))
                put("sarcasm", request.settings.sarcasm.coerceIn(0, 100))
                put("formality", request.settings.formality.coerceIn(0, 100))
                put("proactivity", request.settings.proactivity.coerceIn(0, 100))
            })
        }.toString().toByteArray(Charsets.UTF_8)
        if (body.size > 32_768) return unavailable("O contexto excedeu o limite. Desative o compartilhamento de histórico ou memória e tente novamente.")
        return withTimeoutOrNull(65_000) { suspendCancellableCoroutine { continuation ->
            val connection = AtomicReference<HttpsURLConnection?>()
            val future = AtomicReference<Future<*>?>()
            continuation.invokeOnCancellation {
                connection.get()?.disconnect()
                future.get()?.cancel(true)
            }
            try {
                val work = executor.submit {
                    val result = try {
                        if (!continuation.isActive) return@submit
                        val active = endpoint.openConnection() as HttpsURLConnection
                        connection.set(active)
                        if (!continuation.isActive) return@submit
                        active.apply {
                            requestMethod = "POST"
                            connectTimeout = 10_000
                            readTimeout = 55_000
                            instanceFollowRedirects = false
                            useCaches = false
                            doOutput = true
                            setRequestProperty("Authorization", authorization)
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            setRequestProperty("Accept", "application/json")
                            setFixedLengthStreamingMode(body.size)
                        }
                        active.outputStream.use { it.write(body) }
                        when (active.responseCode) {
                            200 -> {
                                val bytes = ByteArrayOutputStream()
                                active.inputStream.use { input ->
                                    val buffer = ByteArray(4_096)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        require(bytes.size() + count <= 65_536)
                                        bytes.write(buffer, 0, count)
                                    }
                                }
                                val answer = JSONObject(bytes.toString("UTF-8")).optString("text")
                                if (answer.isBlank() || answer.length > 16_000) unavailable("O servidor enviou uma resposta inválida.")
                                else ProviderResult.Success(answer)
                            }
                            401, 403 -> unavailable(accessError())
                            413 -> unavailable("O servidor recusou o tamanho da mensagem ou do contexto.")
                            429 -> unavailable("Limite temporário de solicitações. Aguarde e tente novamente.")
                            502, 503, 504 -> unavailable("O modelo está indisponível ou demorou para responder. Tente novamente.")
                            else -> unavailable("O servidor não conseguiu concluir a solicitação.")
                        }
                    } catch (_: SocketTimeoutException) {
                        unavailable("O servidor demorou para responder. Tente novamente.")
                    } catch (_: Exception) {
                        unavailable("Não foi possível acessar a IA. Verifique a conexão e a configuração do servidor.")
                    } finally { connection.getAndSet(null)?.disconnect() }
                    if (continuation.isActive) continuation.resume(result)
                }
                future.set(work)
                if (!continuation.isActive) work.cancel(true)
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                if (continuation.isActive) continuation.resume(unavailable("Há solicitações em andamento. Tente novamente em instantes."))
            }
        } } ?: unavailable("A solicitação excedeu o tempo máximo. Tente novamente.")
    }

    private fun unavailable(reason: String) = ProviderResult.Unavailable(reason)

    private fun accessError() = if (accessMode == AiAccessMode.LocalGateway) {
        "Configure ou atualize o token pessoal do servidor de IA nas conexões. O modo local não exige conta."
    } else {
        "Sua sessão não está disponível ou foi recusada pelo servidor de IA. Entre novamente e verifique a conta autorizada."
    }

    private companion object {
        val executor = ThreadPoolExecutor(
            2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(16),
            { runnable -> Thread(runnable, "auron-ai-http").apply { isDaemon = true } },
        )
    }
}
