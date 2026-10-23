package br.com.auron.cloud

import br.com.auron.auth.AuthPolicies
import br.com.auron.auth.ConnectionConfig
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Explicit, bounded HTTPS operations. No background sync or local database writes. */
class SupabaseCloudClient(
    private val config: ConnectionConfig,
    private val tokenProvider: suspend () -> String?,
) {
    suspend fun fetch(): CloudSnapshot? = parseSnapshot(rpc("auron_snapshot_get", "{}"))

    suspend fun save(exportJson: String, expectedRevision: Long): CloudSnapshot {
        CloudPolicy.requireRevision(expectedRevision)
        val body = withContext(Dispatchers.Default) {
            CloudPolicy.requirePayloadSize(exportJson.toByteArray(Charsets.UTF_8).size)
            val payload = try { JSONObject(exportJson) } catch (_: Exception) { throw invalidPayload() }
            validatePayload(payload)
            JSONObject().put("p_payload", payload).put("p_expected_revision", expectedRevision).toString()
        }
        return parseSnapshot(rpc("auron_snapshot_save", body)) ?: throw invalidResponse()
    }

    suspend fun delete(expectedRevision: Long) {
        CloudPolicy.requireRevision(expectedRevision, deleting = true)
        val response = rpc("auron_snapshot_delete", "{\"p_expected_revision\":$expectedRevision}")
        val deleted = withContext(Dispatchers.Default) {
            runCatching { JSONObject(response).getBoolean("deleted") }.getOrDefault(false)
        }
        if (!deleted) throw invalidResponse()
    }

    private suspend fun parseSnapshot(response: String): CloudSnapshot? = withContext(Dispatchers.Default) {
        if (response.trim() == "null") return@withContext null
        try {
            val record = JSONObject(response)
            val revision = record.getLong("revision")
            val updated = record.getString("updatedAt")
            require(revision > 0 && updated.length in 1..80)
            val payload = record.getJSONObject("payload")
            validatePayload(payload)
            val json = payload.toString()
            CloudPolicy.requirePayloadSize(json.toByteArray(Charsets.UTF_8).size)
            CloudSnapshot(revision, updated, json, sections.associateWith { payload.getJSONArray(it).length() })
        } catch (_: Exception) { throw invalidResponse() }
    }

    private fun validatePayload(payload: JSONObject) {
        try {
            require(payload.getString("format") == "auron-export" && payload.getInt("version") == 2)
            require(payload.getString("exportedAt").length in 1..80)
            payload.getJSONObject("settings")
            sections.forEach { payload.getJSONArray(it) }
            val allowed = sections.toSet() + setOf("format", "version", "exportedAt", "settings")
            require(payload.keys().asSequence().all { it in allowed })
        } catch (_: Exception) { throw invalidPayload() }
    }

    private suspend fun rpc(name: String, payload: String): String {
        val normalized = try {
            AuthPolicies.normalize(config).also { require(it.supabaseUrl.isNotBlank()) }
        } catch (_: Exception) {
            throw CloudFailure(CloudFailureKind.CONFIGURATION, "Configure a URL HTTPS e a chave pública do Supabase antes de usar a nuvem.")
        }
        val uri = URI(normalized.supabaseUrl)
        val endpoint = URI("https", null, uri.host, uri.port, "/rest/v1/rpc/$name", null, null).toURL()
        val token = try { tokenProvider() } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { throw sessionFailure() }
        if (token.isNullOrBlank() || token.length > 32_768 || token.any { it.isWhitespace() || it.isISOControl() }) throw sessionFailure()
        val bytes = withContext(Dispatchers.Default) { payload.toByteArray(Charsets.UTF_8) }
        return withTimeoutOrNull(35_000L) {
            suspendCancellableCoroutine<String> { continuation ->
                val connection = AtomicReference<HttpsURLConnection?>()
                val future = AtomicReference<Future<*>?>()
                continuation.invokeOnCancellation {
                    connection.get()?.disconnect()
                    future.get()?.cancel(true)
                }
                try {
                    val work = executor.submit {
                        try {
                            if (!continuation.isActive) return@submit
                            val active = endpoint.openConnection() as HttpsURLConnection
                            connection.set(active)
                            if (!continuation.isActive) return@submit
                            active.apply {
                                requestMethod = "POST"
                                instanceFollowRedirects = false
                                connectTimeout = 10_000
                                readTimeout = 15_000
                                useCaches = false
                                doOutput = true
                                setRequestProperty("apikey", normalized.supabasePublishableKey)
                                setRequestProperty("Authorization", "Bearer $token")
                                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                setRequestProperty("Accept", "application/json")
                                setFixedLengthStreamingMode(bytes.size)
                            }
                            active.outputStream.use { it.write(bytes) }
                            if (active.responseCode !in 200..299) throw httpFailure(active.responseCode)
                            val response = active.inputStream.use { input ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(4096)
                                while (true) {
                                    if (!continuation.isActive) return@submit
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > CloudPolicy.MAX_RESPONSE_BYTES) throw invalidResponse()
                                    output.write(buffer, 0, count)
                                }
                                output.toString("UTF-8")
                            }
                            if (continuation.isActive) continuation.resume(response)
                        } catch (failure: Exception) {
                            val safe = when (failure) {
                                is CloudFailure -> failure
                                is SocketTimeoutException -> CloudFailure(CloudFailureKind.UNAVAILABLE, "O Supabase demorou para responder. Consulte a cópia novamente antes de repetir a alteração.")
                                else -> CloudFailure(CloudFailureKind.UNAVAILABLE, "Não foi possível acessar a nuvem com segurança. Confira a conexão e consulte novamente; os dados locais foram preservados.")
                            }
                            if (continuation.isActive) continuation.resumeWithException(safe)
                        } finally { connection.getAndSet(null)?.disconnect() }
                    }
                    future.set(work)
                    if (!continuation.isActive) work.cancel(true)
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    if (continuation.isActive) continuation.resumeWithException(CloudFailure(CloudFailureKind.UNAVAILABLE, "Há solicitações em andamento. Tente novamente em instantes."))
                }
            }
        } ?: throw CloudFailure(CloudFailureKind.UNAVAILABLE, "A solicitação excedeu o tempo máximo. Consulte a cópia antes de repetir: a operação remota pode ter sido concluída.")
    }

    private fun httpFailure(status: Int): CloudFailure = when (status) {
        401 -> sessionFailure()
        403 -> CloudFailure(CloudFailureKind.FORBIDDEN, "A nuvem aceita apenas o proprietário com e-mail confirmado. Confira a conta e a política no Supabase.")
        409 -> CloudFailure(CloudFailureKind.CONFLICT, "A cópia mudou desde a consulta. Consulte novamente antes de substituir ou excluir.")
        413 -> CloudFailure(CloudFailureKind.TOO_LARGE, "O servidor recusou a cópia por exceder 1 MiB. Os registros locais foram preservados.")
        404 -> CloudFailure(CloudFailureKind.CONFIGURATION, "As funções de nuvem não estão disponíveis. Aplique a migração SQL do AURON no projeto Supabase.")
        429 -> CloudFailure(CloudFailureKind.UNAVAILABLE, "Limite temporário do serviço. Aguarde antes de consultar novamente.")
        else -> CloudFailure(CloudFailureKind.UNAVAILABLE, "O Supabase não concluiu a solicitação. Confira a migração e a conexão e consulte novamente.")
    }

    private fun sessionFailure() = CloudFailure(CloudFailureKind.SESSION, "Entre novamente na conta do proprietário para acessar a nuvem.")
    private fun invalidResponse() = CloudFailure(CloudFailureKind.INVALID_RESPONSE, "A nuvem retornou um registro incompatível. Nenhum registro local foi alterado.")
    private fun invalidPayload() = CloudFailure(CloudFailureKind.INVALID_RESPONSE, "A exportação não está no formato AURON v2. Nenhum registro local foi alterado.")

    private companion object {
        val sections = listOf("tasks", "routineItems", "routineCompletions", "messages", "memories", "readings")
        val executor = ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(8),
            { runnable -> Thread(runnable, "auron-cloud-http").apply { isDaemon = true } })
    }
}
