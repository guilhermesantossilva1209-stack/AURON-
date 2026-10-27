package br.com.auron.auth

/** Known protocol errors only. Never display server bodies, addresses, tokens or passwords. */
internal object AuthErrors {
    data class Result(val message: String, val invalidSession: Boolean = false)

    fun fromServer(status: Int, code: String, serverMessage: String = ""): Result {
        val invalidSession = code in setOf("refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired", "bad_jwt", "user_not_found", "user_banned")
        val message = when {
            status in 300..399 -> "O servidor tentou redirecionar a autenticação. Confira a URL HTTPS raiz do projeto em Configurar conexões."
            serverMessage.equals("Invalid API key", ignoreCase = true) || code == "invalid_api_key" ->
                "A chave pública do Supabase não foi aceita. Em Configurar conexões, confira se a URL e a chave publishable pertencem ao mesmo projeto. Não use a senha da conta como chave."
            status == 429 || code in setOf("over_request_rate_limit", "over_email_send_rate_limit", "over_sms_send_rate_limit") ->
                "Muitas tentativas ou e-mails solicitados. Aguarde alguns minutos antes de tentar novamente."
            code == "email_not_confirmed" -> "Confirme seu e-mail antes de entrar. Verifique também a pasta de spam."
            code == "email_address_not_authorized" -> "O Supabase não pode enviar e-mails para este endereço com o envio atual. Configure o SMTP autorizado no painel ou use o e-mail autorizado da equipe do projeto."
            code == "email_address_invalid" -> "O serviço recusou este endereço de e-mail. Confira o endereço e use um e-mail real do proprietário."
            code == "weak_password" -> "O servidor recusou a senha por segurança. Use uma senha mais longa e variada."
            code == "signup_disabled" -> "O cadastro está desativado no Supabase. Se sua conta já existe, use Entrar. Para o primeiro cadastro, ative e teste o Hook do proprietário antes de habilitar novos cadastros no painel."
            code == "email_provider_disabled" -> "O acesso por e-mail e senha está desativado no Supabase. Confira Authentication → Sign In / Providers no painel do projeto."
            code == "user_already_exists" || code == "email_exists" -> "Não foi possível cadastrar este e-mail. Tente entrar ou recuperar a senha."
            code == "captcha_failed" -> "O serviço exige CAPTCHA, que esta versão não oferece. A configuração de autenticação do projeto precisa ser compatível com o aplicativo."
            code.startsWith("hook_") -> "A proteção de cadastro do Supabase não concluiu a solicitação. Confira o Auth Hook e seus registros no painel; não desative a proteção para contornar o erro."
            code == "request_timeout" || status == 408 || status == 504 -> "O serviço de autenticação demorou para responder. Verifique a conexão e tente novamente."
            invalidSession -> "Sua sessão expirou ou foi revogada. Entre novamente."
            code == "invalid_credentials" -> "E-mail ou senha não aceitos. Se ainda não criou sua conta, use Primeiro acesso do proprietário; se já criou, confira os dados ou recupere a senha."
            status == 404 -> "O endereço de autenticação não foi encontrado. Em Configurar conexões, use a Project URL raiz do Supabase, sem /auth/v1."
            status == 401 -> "A configuração pública de autenticação não foi aceita. Confira a Project URL e a chave publishable do mesmo projeto em Configurar conexões."
            status == 403 -> "O serviço recusou o acesso. Confira a política do proprietário e a configuração de autenticação no painel do Supabase."
            status >= 500 -> "O serviço de autenticação está indisponível. Tente novamente mais tarde; o modo local continua disponível."
            status == 400 || status == 422 -> "O serviço não aceitou os dados ou a configuração da solicitação. Confira os campos e a configuração do projeto Supabase."
            else -> "O serviço não concluiu a solicitação. Confira os dados e tente novamente."
        }
        return Result(message, invalidSession)
    }
}
