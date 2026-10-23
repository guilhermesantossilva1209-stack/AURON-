package br.com.auron.auth

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Global connection/session owner. Profile data is opened separately by the ViewModel. */
class AuthManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("auron.connections.v1", Context.MODE_PRIVATE)
    private val sessions = EncryptedSessionStore(context.applicationContext)
    private val client = SupabaseAuthClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex() // Login/configuration/refresh are serialized; refresh is single-flight.
    private val identityEpoch = AtomicLong(0L)
    private val identityLock = Any()
    private var refreshRetryAfterSeconds = 0L
    @Volatile private var closed = false
    private val mutableState = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = mutableState.asStateFlow()
    private val mutableConfig = MutableStateFlow(ConnectionConfig())
    val config: StateFlow<ConnectionConfig> = mutableConfig.asStateFlow()

    init {
        val startupEpoch = identityEpoch.get()
        scope.launch {
            mutex.withLock {
                if (closed || startupEpoch != identityEpoch.get()) return@withLock
                try {
                    val saved = AuthPolicies.normalize(ConnectionConfig(
                        preferences.getString("supabase_url", "").orEmpty(),
                        preferences.getString("supabase_public_key", "").orEmpty(),
                        preferences.getString("ai_url", "").orEmpty(),
                    ))
                    mutableConfig.value = saved
                    // First launch shows account access. An explicitly chosen local
                    // profile remains available offline without erasing legacy data.
                    val local = preferences.getBoolean("local_mode", false)
                    val session = if (!local && saved.supabaseUrl.isNotEmpty() && preferences.getBoolean("restore_session", false)) sessions.read(saved.supabaseUrl) else null
                    mutate(startupEpoch) { AuthState(ready = true, session = session, localMode = local) }
                } catch (_: Exception) {
                    runCatching { sessions.clear() }
                    mutate(startupEpoch) { AuthState(ready = true, localMode = false,
                        error = "Não foi possível restaurar a sessão ou conexão. O perfil local continua disponível; confira a configuração e entre novamente.") }
                }
            }
        }
    }

    suspend fun signIn(email: String, password: String) = operation {
        val epoch = identityEpoch.get()
        val config = requireConfigured()
        val address = AuthPolicies.email(email)
        AuthPolicies.password(password, registering = false)
        val response = client.post(config, "token?grant_type=password", JSONObject().put("email", address).put("password", password))
        val session = client.session(response) ?: throw AuthFailure("O servidor não retornou uma sessão. Confirme seu e-mail e tente novamente.")
        acceptSession(config, session, epoch)
    }

    suspend fun signUp(email: String, password: String, ownerPolicyConfirmed: Boolean = false) = operation {
        // The checkbox is setup acknowledgement, not a security boundary. Supabase
        // auron_before_user_created must reject every non-owner on the server.
        AuthPolicies.requireControlledRegistrationConfirmed(ownerPolicyConfirmed)
        val epoch = identityEpoch.get()
        val config = requireConfigured()
        val address = AuthPolicies.email(email)
        AuthPolicies.password(password, registering = true)
        val response = client.post(config, "signup", JSONObject().put("email", address).put("password", password))
        val session = client.session(response)
        if (session != null) acceptSession(config, session, epoch)
        else mutate(epoch) { it.copy(notice = "Solicitação recebida. Se o cadastro puder ser concluído, você receberá um e-mail de confirmação. Depois, volte ao AURON para entrar.") }
    }

    suspend fun requestPasswordReset(email: String) = operation {
        val epoch = identityEpoch.get()
        val config = requireConfigured()
        // No arbitrary/deep-link redirect: Supabase uses its configured HTTPS Site URL.
        client.post(config, "recover", JSONObject().put("email", AuthPolicies.email(email)))
        mutate(epoch) { it.copy(passwordRecovery = true,
            notice = "Se houver uma conta elegível, você receberá um link para definir a senha na página HTTPS configurada pelo responsável do serviço. Depois, volte ao AURON e entre.") }
    }

    suspend fun signOut() {
        val (previous, config, epoch) = synchronized(identityLock) {
            val oldSession = mutableState.value.session
            val oldConfig = mutableConfig.value
            val nextEpoch = switchIdentity(local = false)
            Triple(oldSession, oldConfig, nextEpoch)
        }
        operation(epoch) {
        invalidateStoredSession(local = false)
        if (previous != null && config.supabaseUrl.isNotEmpty() && epoch == identityEpoch.get()) {
            try {
                client.post(config, "logout?scope=local", JSONObject(), previous.accessToken)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutate(epoch) { it.copy(notice = "Sessão removida deste aparelho. Não foi possível confirmar a revogação no servidor.") }
            }
        }
        }
    }

    suspend fun saveConfig(config: ConnectionConfig) = operation {
        val epoch = identityEpoch.get()
        val normalized = AuthPolicies.normalize(config)
        val previous = mutableConfig.value
        val projectChanged = previous.supabaseUrl != normalized.supabaseUrl || previous.supabasePublishableKey != normalized.supabasePublishableKey
        if (projectChanged) {
            // Invalidate identity before publishing a new endpoint to avoid sending an
            // old bearer token to another project while repositories are switching.
            mutate(epoch) { it.copy(session = null) }
            invalidateStoredSession(local = mutableState.value.localMode)
        }
        check(preferences.edit().putString("supabase_url", normalized.supabaseUrl)
            .putString("supabase_public_key", normalized.supabasePublishableKey)
            .putString("ai_url", normalized.aiBaseUrl).commit())
        mutableConfig.value = normalized
        mutate(epoch) { it.copy(notice = if (projectChanged) "Conexão salva. Entre novamente para usar uma conta." else "Conexão salva.") }
    }

    fun enterLocalMode() {
        if (closed) return
        val epoch = switchIdentity(local = true)
        scope.launch { operation(epoch) {
            invalidateStoredSession(local = true)
            mutate(epoch) { it.copy(notice = "Perfil local aberto. Seus registros anteriores foram preservados.") }
        } }
    }

    fun showLogin() {
        if (closed) return
        val epoch = switchIdentity(local = false)
        scope.launch { operation(epoch) { invalidateStoredSession(local = false) } }
    }

    fun clearMessages() {
        mutate { it.copy(error = null, notice = null, passwordRecovery = false) }
    }

    /** Returns null on missing/expired credentials; never returns a known-expired bearer. */
    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed || mutableState.value.localMode) return@withLock null
            val epoch = identityEpoch.get()
            val previous = mutableState.value.session ?: return@withLock null
            val now = System.currentTimeMillis() / 1000L
            if (!AuthPolicies.needsRefresh(previous.expiresAt, now)) return@withLock previous.accessToken
            if (now < refreshRetryAfterSeconds) return@withLock null
            try {
                val config = requireConfigured()
                val refreshed = client.session(client.post(config, "token?grant_type=refresh_token", JSONObject().put("refresh_token", previous.refreshToken)))
                    ?: throw AuthFailure("A sessão expirou. Entre novamente.", invalidSession = true)
                if (refreshed.userId != previous.userId) throw AuthFailure("O servidor retornou uma identidade diferente. Entre novamente.", invalidSession = true)
                if (acceptSession(config, refreshed, epoch)) refreshed.accessToken else null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!closed && epoch == identityEpoch.get() && error is AuthFailure && error.invalidSession) {
                    mutate(epoch) { it.copy(session = null, localMode = false, error = "Sua sessão expirou ou foi revogada. Entre novamente.") }
                    runCatching { invalidateStoredSession(local = false) }
                } else if (!closed && epoch == identityEpoch.get()) {
                    refreshRetryAfterSeconds = System.currentTimeMillis() / 1000L + 5L
                    mutate(epoch) { it.copy(error = "Não foi possível renovar sua sessão. Confira a conexão e tente novamente.") }
                }
                null
            }
        }
    }

    fun close() {
        synchronized(identityLock) { closed = true; identityEpoch.incrementAndGet() }
        client.close()
        scope.cancel()
    }

    private suspend fun operation(epoch: Long = identityEpoch.get(), block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed || epoch != identityEpoch.get()) return@withLock
            mutate(epoch) { it.copy(busy = true, error = null, notice = null, passwordRecovery = false) }
            try {
                block()
                currentCoroutineContext().ensureActive()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutate(epoch) { it.copy(error = when (error) {
                    is AuthFailure -> error.safeMessage
                    is IllegalArgumentException -> error.message ?: "Confira os dados informados."
                    else -> "Não foi possível concluir a operação com segurança. Tente novamente; os dados locais foram preservados."
                }) }
            } finally {
                mutate(epoch) { it.copy(busy = false, ready = true) }
            }
        }
    }

    private fun requireConfigured(): ConnectionConfig = mutableConfig.value.also {
        if (it.supabaseUrl.isBlank() || it.supabasePublishableKey.isBlank()) throw AuthFailure("Autenticação externa não configurada. Configure um projeto Supabase ou continue no modo local.")
    }

    private fun acceptSession(config: ConnectionConfig, session: AuthSession, epoch: Long): Boolean {
        if (closed || epoch != identityEpoch.get()) return false
        sessions.save(config.supabaseUrl, session)
        refreshRetryAfterSeconds = 0L
        if (closed || epoch != identityEpoch.get()) return false
        check(preferences.edit().putBoolean("local_mode", false).putBoolean("restore_session", true).commit())
        return mutate(epoch) { it.copy(session = session, localMode = false, error = null, notice = null, passwordRecovery = false) }
    }

    private fun invalidateStoredSession(local: Boolean) {
        val restorationDisabled = runCatching {
            preferences.edit().putBoolean("local_mode", local).putBoolean("restore_session", false).commit()
        }.getOrDefault(false)
        val credentialsErased = runCatching { sessions.clear() }.isSuccess
        if (!restorationDisabled && !credentialsErased) throw AuthFailure("Não foi possível remover a sessão persistida. O acesso foi fechado nesta execução; verifique o armazenamento do dispositivo antes de reabrir o app.")
    }

    private fun switchIdentity(local: Boolean): Long = synchronized(identityLock) {
        val epoch = identityEpoch.incrementAndGet()
        if (!closed) mutableState.value = AuthState(ready = true, localMode = local, busy = true)
        epoch
    }

    private fun mutate(epoch: Long? = null, transform: (AuthState) -> AuthState): Boolean = synchronized(identityLock) {
        if (closed || (epoch != null && epoch != identityEpoch.get())) return@synchronized false
        mutableState.value = transform(mutableState.value)
        true
    }
}
