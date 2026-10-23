package br.com.auron.auth

data class ConnectionConfig(
    val supabaseUrl: String = "",
    val supabasePublishableKey: String = "",
    val aiBaseUrl: String = "",
)

data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    /** Unix epoch seconds, as returned by Supabase; never milliseconds. */
    val expiresAt: Long,
)

data class AuthState(
    val ready: Boolean = false,
    val session: AuthSession? = null,
    val localMode: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val passwordRecovery: Boolean = false,
)
