package br.com.auron.ai

/** Local means no AURON account, not anonymous access to an inference server. */
enum class AiAccessMode { SupabaseSession, LocalGateway }

object AiAccess {
    private val localToken = Regex("auron_local_[A-Za-z0-9_-]{43,128}")
    private val sessionToken = Regex("[A-Za-z0-9._~+/\\-]{1,8192}")

    fun requireLocalToken(value: String) {
        require(localToken.matches(value)) {
            "Use o token pessoal auron_local_… gerado no servidor AURON; não use chaves do Supabase ou do provedor de IA."
        }
    }

    /** Null prevents a network request. Credentials never change authentication modes implicitly. */
    fun authorization(mode: AiAccessMode, token: String?): String? = when {
        token == null -> null
        mode == AiAccessMode.LocalGateway && localToken.matches(token) -> "AuronLocal $token"
        mode == AiAccessMode.SupabaseSession && !token.startsWith("auron_local_") && sessionToken.matches(token) -> "Bearer $token"
        else -> null
    }
}
