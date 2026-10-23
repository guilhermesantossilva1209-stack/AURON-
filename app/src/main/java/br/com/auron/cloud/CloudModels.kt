package br.com.auron.cloud

data class CloudSnapshot(
    val revision: Long,
    val updatedAt: String,
    val payloadJson: String,
    val counts: Map<String, Int>,
)

data class CloudUiState(
    val loading: Boolean = false,
    val inspected: Boolean = false,
    val snapshot: CloudSnapshot? = null,
    val error: String? = null,
    val notice: String? = null,
)

enum class CloudFailureKind { CONFIGURATION, SESSION, FORBIDDEN, CONFLICT, TOO_LARGE, UNAVAILABLE, INVALID_RESPONSE }

class CloudFailure(val kind: CloudFailureKind, val safeMessage: String) : Exception(safeMessage)

/** No account IDs, credentials or personal content are accepted by this policy. */
object CloudPolicy {
    const val MAX_PAYLOAD_BYTES = 1_048_576
    const val MAX_RESPONSE_BYTES = 1_114_112
    fun requireRevision(revision: Long, deleting: Boolean = false) {
        require(revision >= if (deleting) 1 else 0) { "Consulte a cópia antes de alterá-la." }
    }
    fun requirePayloadSize(bytes: Int) {
        if (bytes !in 1..MAX_PAYLOAD_BYTES) throw CloudFailure(CloudFailureKind.TOO_LARGE,
            "A cópia excedeu o limite de 1 MiB. Seus dados locais foram preservados; nenhum envio foi feito.")
    }
}
