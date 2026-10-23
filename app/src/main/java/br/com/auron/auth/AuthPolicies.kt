package br.com.auron.auth

import java.net.URI
import java.util.Base64

/** Pure validation: configuration contains public routing information, never provider secrets. */
object AuthPolicies {
    /** Client consent gate, not authorization. The configured Supabase Auth Hook
     * must enforce the single authorized email even when called outside this app.
     */
    fun requireControlledRegistrationConfirmed(ownerPolicyConfirmed: Boolean) {
        require(ownerPolicyConfirmed) {
            "Configure a lista do proprietário e ative o Auth Hook no Supabase antes de criar a conta."
        }
    }

    fun normalize(config: ConnectionConfig): ConnectionConfig {
        val normalized = config.copy(
            supabaseUrl = config.supabaseUrl.trim().trimEnd('/'),
            supabasePublishableKey = config.supabasePublishableKey.trim(),
            aiBaseUrl = config.aiBaseUrl.trim().trimEnd('/'),
        )
        require((normalized.supabaseUrl.isEmpty()) == (normalized.supabasePublishableKey.isEmpty())) {
            "Informe a URL e a chave pública do Supabase juntas, ou deixe ambas vazias para usar o modo local."
        }
        if (normalized.supabaseUrl.isNotEmpty()) {
            requireHttps(normalized.supabaseUrl)
            require(URI(normalized.supabaseUrl).path.orEmpty().isEmpty()) { "A URL do Supabase deve ser a raiz do projeto, sem /auth/v1 ou outro caminho." }
            requirePublicKey(normalized.supabasePublishableKey)
        }
        if (normalized.aiBaseUrl.isNotEmpty()) {
            requireHttps(normalized.aiBaseUrl)
            require(URI(normalized.aiBaseUrl).path.orEmpty().isEmpty()) { "A URL de IA deve ser a raiz do servidor, sem /v1/chat ou outro caminho." }
        }
        return normalized
    }

    fun requireHttps(value: String) {
        require(value.length <= 2048 && value.none { it.isWhitespace() || it.code < 32 }) { "Informe uma URL HTTPS válida." }
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri != null && uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port in 1..65535)
        ) { "Use HTTPS, sem usuário, senha, parâmetros ou fragmentos na URL." }
    }

    fun requirePublicKey(key: String) {
        require(key.length in 20..8192 && key.none(Char::isWhitespace)) { "Informe uma chave pública publishable ou anon válida do Supabase." }
        require(!key.startsWith("sb_secret_")) { "Chaves secret/service_role não podem ser usadas no aplicativo." }
        if (key.startsWith("sb_publishable_")) {
            require(key.matches(Regex("sb_publishable_[A-Za-z0-9_-]+"))) { "A chave publishable contém caracteres inválidos." }
            return
        }
        val pieces = key.split('.')
        require(pieces.size == 3) { "Use uma chave publishable ou a chave legada anon, nunca uma chave de provedor de IA." }
        val payload = runCatching { String(Base64.getUrlDecoder().decode(pieces[1]), Charsets.UTF_8) }.getOrNull()
        val roles = payload?.let { Regex("\"role\"\\s*:\\s*\"([^\"]*)\"").findAll(it).map { match -> match.groupValues[1] }.toList() }.orEmpty()
        // This is a guard against configuring a privileged key, not JWT authentication.
        // Authentication and JWT verification remain exclusively on Supabase/the backend.
        require(roles.size == 1 && roles.single() == "anon" && payload?.contains("service_role") != true) {
            "A chave legada precisa ter role anon. Chaves secret/service_role são rejeitadas."
        }
    }

    fun email(value: String): String {
        val email = value.trim()
        require(email.length in 3..320 && email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "Informe um e-mail válido." }
        return email
    }

    fun password(value: String, registering: Boolean) {
        require(value.isNotEmpty() && value.length <= 4096) { "Informe uma senha válida." }
        if (registering) require(value.length >= 8) { "Use pelo menos 8 caracteres na senha. O servidor pode exigir critérios adicionais." }
    }

    fun needsRefresh(expiresAt: Long, nowSeconds: Long): Boolean = expiresAt <= nowSeconds + 60L
}
