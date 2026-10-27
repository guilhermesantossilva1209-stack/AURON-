package br.com.auron.ui

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.auron.ai.AiAccess
import br.com.auron.auth.AuthPolicies
import br.com.auron.auth.AuthState
import br.com.auron.auth.ConnectionConfig
import br.com.auron.ui.theme.AuronColors

private enum class AccessPanel { Login, Register, Reset }

/** Account access remains optional. Passwords are never stored in saved-instance state. */
@Composable
fun AuthScreen(
    state: AuthState,
    configured: Boolean,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onReset: (String) -> Unit,
    onLocal: () -> Unit,
    onConfigure: () -> Unit,
) {
    var panel by remember { mutableStateOf(AccessPanel.Login) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var acceptedAccountTransfer by remember { mutableStateOf(false) }
    var ownerPolicyConfirmed by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    // Editing the form does not contact a service. Only submission needs a ready connection.
    val editable = !state.busy
    val canSubmit = state.ready && editable

    fun changePanel(next: AccessPanel) {
        focus.clearFocus()
        panel = next
        password = ""
        confirmation = ""
        passwordVisible = false
        acceptedAccountTransfer = false
        ownerPolicyConfirmed = false
        validation = null
    }

    fun submit() {
        if (!canSubmit) return
        if (!configured) {
            // The primary action must explain/fix missing setup instead of silently
            // ignoring the tap. No credentials leave this screen in this branch.
            focus.clearFocus()
            onConfigure()
            return
        }
        val address = email.trim()
        validation = when {
            !Patterns.EMAIL_ADDRESS.matcher(address).matches() -> "Digite um e-mail válido."
            panel != AccessPanel.Reset && password.isEmpty() -> "Digite sua senha."
            panel == AccessPanel.Register && password.length < 8 -> "Crie uma senha com pelo menos 8 caracteres."
            panel == AccessPanel.Register && password != confirmation -> "As senhas precisam ser iguais."
            panel == AccessPanel.Register && !ownerPolicyConfirmed -> "Configure e confirme a proteção do primeiro cadastro no Supabase."
            panel == AccessPanel.Register && !acceptedAccountTransfer -> "Confirme o envio dos dados para criar sua conta."
            else -> null
        }
        if (validation != null) return
        focus.clearFocus()
        when (panel) {
            AccessPanel.Login -> onLogin(address, password)
            AccessPanel.Register -> onRegister(address, password)
            AccessPanel.Reset -> onReset(address)
        }
        password = ""
        confirmation = ""
        passwordVisible = false
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(AuronColors.Background).imePadding().testTag("screen_auth"),
    ) {
        val wide = maxWidth >= 840.dp
        val content: @Composable () -> Unit = {
            Surface(
                color = AuronColors.Surface,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, AuronColors.Gold.copy(alpha = 0.22f)),
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            ) {
                Column(Modifier.padding(if (wide) 30.dp else 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("ACESSO AO SISTEMA", style = MaterialTheme.typography.labelMedium, color = AuronColors.Gold)
                    Text(
                        when (panel) {
                            AccessPanel.Login -> "Seu espaço de comando"
                            AccessPanel.Register -> "Crie a conta do proprietário"
                            AccessPanel.Reset -> "Recupere seu acesso"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        when (panel) {
                            AccessPanel.Login -> "Entre com sua conta ou continue com os dados locais deste dispositivo."
                            AccessPanel.Register -> "Primeiro acesso privado: somente o e-mail autorizado no seu projeto Supabase pode criar a conta. A proteção precisa estar ativa no servidor antes de enviar este formulário."
                            AccessPanel.Reset -> "Ao solicitar a recuperação, seu e-mail será enviado ao Supabase configurado. O link recebido abre a página HTTPS de recuperação definida pelo responsável pelo serviço, onde você escolhe a nova senha."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AuronColors.Muted,
                    )
                    if (!configured) {
                        AuthMessage(
                            "Nenhum serviço de autenticação está configurado. Os campos podem ser preenchidos, mas entrar, criar conta e recuperar senha exigem configurar a conexão. O modo local continua disponível.",
                            isError = false,
                            modifier = Modifier.testTag("auth_unconfigured"),
                        )
                        TextButton(
                            onClick = { focus.clearFocus(); onConfigure() },
                            enabled = editable,
                            modifier = Modifier.testTag("auth_configure_service"),
                        ) { Text("Configurar autenticação") }
                    }
                    if (!state.ready) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Preparando o acesso…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (panel == AccessPanel.Register) {
                        Column(Modifier.testTag("auth_owner_setup"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("1. Aplique a migração SQL fornecida e cadastre seu próprio e-mail na lista autorizada do Supabase.", style = MaterialTheme.typography.bodyMedium)
                            Text("2. Em Authentication → Hooks, ative auron_before_user_created como Before User Created. Mantenha confirmação de e-mail ativada.", style = MaterialTheme.typography.bodyMedium)
                            Text("3. Após criar e confirmar sua conta, desative Allow new users to sign up. Seus dados locais continuam preservados.", style = MaterialTheme.typography.bodyMedium)
                            Text("O aplicativo não tem acesso administrativo para configurar ou comprovar a ativação do Hook. A caixa abaixo confirma sua preparação; ela não substitui a proteção no servidor.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                        }
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { if (it.length <= 254) { email = it; validation = null } },
                        label = { Text("E-mail") },
                        singleLine = true,
                        enabled = editable,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (panel == AccessPanel.Reset) ImeAction.Done else ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { if (panel != AccessPanel.Reset) passwordFocus.requestFocus() },
                            onDone = { submit() },
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("auth_email"),
                    )
                    if (panel != AccessPanel.Reset) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { if (it.length <= 1024) { password = it; validation = null } },
                            label = { Text("Senha") },
                            enabled = editable,
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (panel == AccessPanel.Register) ImeAction.Next else ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onNext = { if (panel == AccessPanel.Register) confirmationFocus.requestFocus() },
                                onDone = { submit() },
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = editable) {
                                    Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (passwordVisible) "Ocultar senha" else "Mostrar senha")
                                }
                            },
                            supportingText = {
                                if (panel == AccessPanel.Register) {
                                    Text("Mínimo de 8 caracteres. O serviço pode exigir uma senha mais forte.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).testTag("auth_password"),
                        )
                    }
                    if (panel == AccessPanel.Register) {
                        OutlinedTextField(
                            value = confirmation,
                            onValueChange = { if (it.length <= 1024) { confirmation = it; validation = null } },
                            label = { Text("Confirmar senha") },
                            enabled = editable,
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(confirmationFocus).testTag("auth_password_confirmation"),
                        )
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(
                                checked = ownerPolicyConfirmed,
                                onCheckedChange = { ownerPolicyConfirmed = it; validation = null },
                                enabled = editable,
                                modifier = Modifier.testTag("auth_owner_policy_confirmed").semantics { contentDescription = "Confirmar configuração do cadastro privado no Supabase" },
                            )
                            Text(
                                "Configurei a lista do proprietário e ativei o Auth Hook que bloqueia outros e-mails no Supabase.",
                                style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted,
                                modifier = Modifier.weight(1f).padding(top = 10.dp),
                            )
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(
                                checked = acceptedAccountTransfer,
                                onCheckedChange = { acceptedAccountTransfer = it; validation = null },
                                enabled = editable,
                                modifier = Modifier.testTag("auth_registration_consent").semantics { contentDescription = "Autorizar envio dos dados para criar a conta" },
                            )
                            Text(
                                "Autorizo enviar meu e-mail e minha senha ao serviço Supabase configurado para criar a conta. Isso não autoriza o envio de conversas ou memórias à IA.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AuronColors.Muted,
                                modifier = Modifier.weight(1f).padding(top = 10.dp),
                            )
                        }
                    }
                    if (panel == AccessPanel.Login) {
                        Text("Ao entrar, seu e-mail e sua senha serão enviados ao serviço de autenticação configurado. A senha não é salva no aplicativo.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                    }
                    validation?.let { AuthMessage(it, isError = true) }
                    state.error?.let { AuthMessage(it, isError = true, modifier = Modifier.testTag("auth_error")) }
                    state.notice?.let { AuthMessage(it, isError = false, modifier = Modifier.testTag("auth_notice")) }
                    Button(onClick = { submit() }, enabled = canSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("auth_submit")) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(if (state.busy) "Aguarde…" else if (!configured) when (panel) {
                            AccessPanel.Login -> "Configurar para entrar"
                            AccessPanel.Register -> "Configurar para criar conta"
                            AccessPanel.Reset -> "Configurar recuperação"
                        } else when (panel) {
                            AccessPanel.Login -> "Entrar na minha conta"
                            AccessPanel.Register -> "Criar minha conta privada"
                            AccessPanel.Reset -> "Enviar link de recuperação"
                        })
                    }
                    if (panel == AccessPanel.Login) {
                        TextButton(onClick = { changePanel(AccessPanel.Reset) }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("Esqueci minha senha") }
                        OutlinedButton(onClick = { changePanel(AccessPanel.Register) }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("Primeiro acesso do proprietário") }
                    } else {
                        TextButton(onClick = { changePanel(AccessPanel.Login) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.ArrowBack, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Voltar para entrar")
                        }
                    }
                    HorizontalDivider(color = AuronColors.Elevated)
                    OutlinedButton(onClick = onLocal, enabled = state.ready && !state.busy, modifier = Modifier.fillMaxWidth().testTag("auth_local")) {
                        Icon(Icons.Outlined.CloudOff, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Continuar no modo local")
                    }
                    TextButton(onClick = { focus.clearFocus(); onConfigure() }, enabled = editable, modifier = Modifier.fillMaxWidth().testTag("auth_configure")) {
                        Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Configurar conexões")
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 44.dp else 16.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (wide) {
                Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { AuthIdentity(expanded = true) }
                    Box(Modifier.weight(1.15f), contentAlignment = Alignment.Center) { content() }
                }
            } else {
                AuthIdentity(expanded = false)
                Spacer(Modifier.height(28.dp))
                content()
            }
        }
    }
}

@Composable
private fun AuthIdentity(expanded: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AuronColors.Elevated,
            border = BorderStroke(1.dp, AuronColors.Gold.copy(alpha = 0.4f)),
        ) {
            Box(
                Modifier.size(if (expanded) 96.dp else 64.dp).background(Brush.radialGradient(listOf(AuronColors.Gold.copy(alpha = 0.15f), AuronColors.Surface))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Memory, null, tint = AuronColors.Gold, modifier = Modifier.size(if (expanded) 46.dp else 32.dp)) }
        }
        Text("AURON", style = if (expanded) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
        Text("SISTEMA DE INTELIGÊNCIA PESSOAL", style = MaterialTheme.typography.labelMedium, color = AuronColors.Gold)
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Text("Sua rotina.\nSeus projetos.\nSeu comando.", style = MaterialTheme.typography.headlineLarge)
            Text("Uma central pessoal que cresce com você, com controle sobre seus dados e suas conexões.", style = MaterialTheme.typography.bodyLarge, color = AuronColors.Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = AuronColors.Gold)
                Spacer(Modifier.width(8.dp))
                Text("Modo local disponível sem conta", style = MaterialTheme.typography.labelMedium, color = AuronColors.Muted)
            }
        }
    }
}

@Composable
private fun AuthMessage(message: String, isError: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f) else AuronColors.Gold.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = if (isError) AuronColors.Danger else AuronColors.Muted, modifier = Modifier.padding(14.dp))
    }
}

/** Saves local connection settings only; no network call or AI consent is performed here. */
@Composable
fun ConnectionSettings(
    config: ConnectionConfig,
    busy: Boolean,
    onSave: (ConnectionConfig) -> Unit,
    localAiConfigured: Boolean = false,
    localAiError: String? = null,
    onSaveLocalAiAccess: (String, String) -> Unit = { _, _ -> },
    onClearLocalAiAccess: () -> Unit = {},
) {
    var supabaseUrl by remember(config) { mutableStateOf(config.supabaseUrl) }
    var publicKey by remember(config) { mutableStateOf(config.supabasePublishableKey) }
    var aiBaseUrl by remember(config) { mutableStateOf(config.aiBaseUrl) }
    var validation by remember(config) { mutableStateOf<String?>(null) }
    // The credential is entered at runtime and never placed in saved-instance state.
    // Changing the saved endpoint discards an unfinished credential for the old host.
    var localToken by remember(config.aiBaseUrl) { mutableStateOf("") }
    var localValidation by remember(config.aiBaseUrl) { mutableStateOf<String?>(null) }
    val draft = ConnectionConfig(supabaseUrl.trim().trimEnd('/'), publicKey.trim(), aiBaseUrl.trim().trimEnd('/'))
    val dirty = draft != config
    val savedAiEndpoint = config.aiBaseUrl.isNotBlank() && draft.aiBaseUrl == config.aiBaseUrl
    val focus = LocalFocusManager.current

    Column(
        Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).testTag("connection_settings"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Conexões opcionais", style = MaterialTheme.typography.titleLarge)
        Text("O modo local funciona sem estas configurações. Use apenas serviços que você administra ou em que confia.", style = MaterialTheme.typography.bodyMedium, color = AuronColors.Muted)
        Text("AUTENTICAÇÃO · SUPABASE", style = MaterialTheme.typography.labelMedium, color = AuronColors.Gold)
        OutlinedTextField(
            value = supabaseUrl,
            onValueChange = { if (it.length <= 2048) { supabaseUrl = it; validation = null } },
            label = { Text("URL do projeto Supabase") },
            placeholder = { Text("https://seu-projeto.supabase.co") },
            supportingText = { Text("Obrigatório HTTPS. Não inclua senhas, parâmetros ou fragmentos na URL.") },
            enabled = !busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("connection_supabase_url"),
        )
        OutlinedTextField(
            value = publicKey,
            onValueChange = { if (it.length <= 8192) { publicKey = it; validation = null } },
            label = { Text("Chave pública do Supabase") },
            supportingText = { Text("Use publishable (sb_publishable_…) ou a chave legada anon. Nunca use service_role, secret ou chaves de provedores de IA.") },
            enabled = !busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("connection_public_key"),
        )
        Text("A conta usa este serviço para acesso e recuperação de senha. Salvar aqui não transfere tarefas, conversas ou memórias.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
        HorizontalDivider(color = AuronColors.Elevated)
        Text("INTELIGÊNCIA ARTIFICIAL · SERVIDOR", style = MaterialTheme.typography.labelMedium, color = AuronColors.Gold)
        OutlinedTextField(
            value = aiBaseUrl,
            onValueChange = { if (it.length <= 2048) { aiBaseUrl = it; validation = null } },
            label = { Text("URL base do servidor de IA") },
            placeholder = { Text("https://seu-servidor.example") },
            supportingText = { Text("Endereço do servidor compatível com o AURON. As chaves do provedor ficam no servidor, nunca neste formulário.") },
            enabled = !busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("connection_ai_url"),
        )
        Text("Com conta, a IA usa sua sessão Supabase. No modo local, ela exige o token pessoal do seu servidor configurado abaixo. Ative a IA nas preferências; histórico e memórias dependem de permissões separadas. Serviços externos podem ter custos; nenhuma assinatura é contratada pelo aplicativo.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
        validation?.let { AuthMessage(it, isError = true, modifier = Modifier.testTag("connection_validation")) }
        Button(
            onClick = {
                val normalized = try {
                    AuthPolicies.normalize(draft).also { validation = null }
                } catch (error: IllegalArgumentException) {
                    validation = error.message ?: "Confira os endereços e a chave pública informados."
                    null
                }
                if (normalized != null) {
                    focus.clearFocus()
                    onSave(normalized)
                }
            },
            enabled = !busy && dirty,
            modifier = Modifier.fillMaxWidth().testTag("connection_save"),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (busy) "Salvando…" else "Salvar conexões")
        }
        Text(
            if (dirty) "Há alterações ainda não salvas. Alterar o serviço de autenticação encerra a sessão atual." else "Sem alterações pendentes. A disponibilidade dos serviços só é confirmada ao utilizá-los.",
            style = MaterialTheme.typography.bodySmall,
            color = AuronColors.Muted,
        )
        HorizontalDivider(color = AuronColors.Elevated)
        Text("IA NO MODO LOCAL · ACESSO PESSOAL", style = MaterialTheme.typography.labelMedium, color = AuronColors.Gold)
        Text("Para conversar com IA sem criar uma conta AURON, configure um servidor AURON com acesso pessoal. O token auron_local_… é gerado nesse servidor e salvo cifrado no Android, separado dos dados exportados. Não informe uma chave de provedor, service_role ou senha do banco.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
        Text(
            if (localAiConfigured) "Acesso pessoal salvo neste dispositivo. A conexão só é confirmada ao enviar uma mensagem."
            else "Nenhum acesso pessoal salvo. Os comandos e registros locais continuam funcionando sem IA externa.",
            style = MaterialTheme.typography.bodySmall,
            color = AuronColors.Muted,
            modifier = Modifier.testTag("connection_local_ai_status"),
        )
        if (!savedAiEndpoint) {
            Text("Salve primeiro a URL base do servidor de IA em Salvar conexões. O token só pode ser associado à URL já salva.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Gold, modifier = Modifier.testTag("connection_local_ai_save_url_first"))
        }
        OutlinedTextField(
            value = localToken,
            onValueChange = { if (it.length <= 256) { localToken = it; localValidation = null } },
            label = { Text("Token pessoal do servidor") },
            supportingText = { Text("auron_local_… · nunca uma chave do provedor de IA") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("connection_local_ai_token"),
        )
        localValidation?.let { AuthMessage(it, isError = true, modifier = Modifier.testTag("connection_local_ai_validation")) }
        localAiError?.let { AuthMessage(it, isError = true, modifier = Modifier.testTag("connection_local_ai_error")) }
        Button(
            onClick = {
                if (!savedAiEndpoint || busy) return@Button
                val credential = localToken.trim()
                val valid = try {
                    AiAccess.requireLocalToken(credential)
                    true
                } catch (_: IllegalArgumentException) {
                    localValidation = "Use o token pessoal auron_local_… gerado no servidor AURON; não use uma chave do Supabase ou do provedor de IA."
                    false
                }
                if (valid) {
                    localValidation = null
                    focus.clearFocus()
                    localToken = ""
                    onSaveLocalAiAccess(config.aiBaseUrl, credential)
                }
            },
            enabled = !busy && savedAiEndpoint && localToken.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("connection_local_ai_save"),
        ) { Text("Salvar acesso pessoal à IA") }
        OutlinedButton(
            onClick = { localToken = ""; localValidation = null; focus.clearFocus(); onClearLocalAiAccess() },
            enabled = !busy && localAiConfigured,
            modifier = Modifier.fillMaxWidth().testTag("connection_local_ai_clear"),
        ) { Text("Remover acesso pessoal deste dispositivo") }
        Text("Remover este acesso não apaga tarefas, memórias ou histórico. Para invalidar o token em outros aparelhos, altere-o também no servidor.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
    }
}
