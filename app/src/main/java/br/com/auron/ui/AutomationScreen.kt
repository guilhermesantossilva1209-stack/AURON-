package br.com.auron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.auron.automation.AutomationSettings
import br.com.auron.automation.AutomationState
import br.com.auron.automation.AuthorizedNotification
import br.com.auron.automation.LaunchableApp
import br.com.auron.automation.NotificationPolicy
import br.com.auron.ui.theme.AuronColors

/** Notification text and reply drafts live only in memory, including Compose state. */
@Composable
fun AutomationScreen(
    state: AutomationState,
    apps: List<LaunchableApp>,
    onSave: (AutomationSettings) -> Unit,
    onAccessSettings: () -> Unit,
    onOpenApp: (LaunchableApp) -> Unit,
    onRead: (String) -> Unit,
    onPrepareReply: (String, String) -> Unit,
    onConfirmReply: (String) -> Unit,
    onCancelReply: () -> Unit,
    onClearResult: () -> Unit,
) {
    var accessConsent by remember(state.profileId) { mutableStateOf(false) }
    var query by remember(state.profileId) { mutableStateOf("") }
    var replyTarget by remember(state.profileId, state.settings.enabled, state.permissionGranted) { mutableStateOf<String?>(null) }
    val profileAvailable = !state.profileId.isNullOrBlank()
    val canRead = profileAvailable && state.settings.enabled && state.permissionGranted && state.connected
    val visibleNotifications = if (canRead) state.notifications.filter { it.appPackage in state.settings.allowedPackages } else emptyList()
    val appChoices = (apps + state.settings.allowedPackages.filter { allowed -> apps.none { it.packageName == allowed } }
        .map { LaunchableApp(it, it) }).distinctBy { it.packageName }
        .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }

    LaunchedEffect(state.pendingReply?.draftId, canRead) {
        if (state.pendingReply != null || !canRead) replyTarget = null
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("screen_automation"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Automações Android", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Seu comando, sua autorização", style = MaterialTheme.typography.titleMedium, color = AuronColors.Gold)
            Spacer(Modifier.height(8.dp))
            Text("Abra um aplicativo pela lista ou diga “abrir aplicativo WhatsApp”. Para consultar mensagens, autorize o acesso do Android e escolha os aplicativos abaixo.", color = AuronColors.Muted)
        }
        item {
            AutomationPanel {
                Text("Acesso às notificações", style = MaterialTheme.typography.titleLarge)
                Text(when {
                    !state.permissionGranted -> "Acesso do Android não autorizado"
                    !state.settings.enabled -> "Acesso concedido · leitura desativada neste perfil"
                    !state.connected -> "Aguardando conexão com o Android"
                    else -> "Leitura autorizada neste perfil"
                }, color = AuronColors.Gold, modifier = Modifier.testTag("automation_access_status"))
                Text("O Android concede acesso amplo às notificações. O AURON usa somente os aplicativos selecionados neste perfil. O conteúdo fica temporariamente na memória do aplicativo e não é salvo no histórico nem enviado à IA.", style = MaterialTheme.typography.bodyMedium, color = AuronColors.Muted)
                OutlinedButton(
                    onClick = { accessConsent = true }, enabled = profileAvailable,
                    modifier = Modifier.fillMaxWidth().testTag("automation_access"),
                ) { Text(if (state.permissionGranted) "Gerenciar ou revogar acesso no Android" else "Autorizar acesso no Android") }
                AutomationToggle("Ler notificações dos aplicativos selecionados", state.settings.enabled, profileAvailable, "automation_enabled") {
                    onSave(state.settings.copy(enabled = it))
                }
                Text("Desativar limpa as notificações e respostas pendentes do AURON. As notificações originais dos aplicativos permanecem no Android.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                AutomationToggle("Avisar sobre novas notificações com o AURON aberto", state.settings.announceForeground,
                    profileAvailable && state.settings.enabled, "automation_announce") { onSave(state.settings.copy(announceForeground = it)) }
                Text("O aviso informa apenas o aplicativo. Para ouvir uma mensagem, toque em “Ler em voz alta”. Os avisos seguem as preferências de voz e não funcionam com o AURON em segundo plano.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                if (state.settings.announceForeground) {
                    Text("Intervalo mínimo entre avisos: ${state.settings.intervalSeconds} segundos", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 120).forEach { seconds ->
                            FilterChip(
                                selected = state.settings.intervalSeconds == seconds,
                                onClick = { onSave(state.settings.copy(intervalSeconds = seconds)) },
                                enabled = profileAvailable && state.settings.enabled,
                                label = { Text("${seconds}s") },
                            )
                        }
                    }
                }
            }
        }
        state.result?.let { message -> item {
            AutomationPanel {
                Text(message, modifier = Modifier.testTag("automation_result"))
                TextButton(onClick = onClearResult) { Text("Entendi") }
            }
        } }
        item {
            Text("Notificações autorizadas", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("O Android pode ocultar conteúdos sensíveis ou não oferecer uma ação de resposta. O AURON mostra apenas o que está disponível na notificação.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
            if (visibleNotifications.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(if (canRead) "Nenhuma notificação autorizada disponível." else "Ative a leitura, conceda o acesso e selecione os aplicativos para consultar as notificações.", color = AuronColors.Muted)
            }
        }
        items(visibleNotifications, key = { "notification:${it.id}" }) { notification ->
            AutomationPanel {
                Text(notification.appLabel, color = AuronColors.Gold, style = MaterialTheme.typography.labelLarge)
                Text(notification.title.ifBlank { "Notificação" }, style = MaterialTheme.typography.titleMedium)
                Text(notification.text.ifBlank { "Conteúdo não disponibilizado pelo aplicativo." }, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { onRead(notification.id) }, modifier = Modifier.testTag("automation_read_${notification.id}")) { Text("Ler em voz alta") }
                if (notification.isWhatsApp) {
                    if (notification.canReply) {
                        Button(onClick = { replyTarget = notification.id }, modifier = Modifier.testTag("automation_reply_${notification.id}")) { Text("Preparar resposta") }
                    } else Text("Esta notificação não oferece resposta direta. Você pode abrir o WhatsApp pela lista de aplicativos.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                }
            }
        }
        item {
            Text("Aplicativos do tablet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Marque quais aplicativos o AURON pode ler. O botão “Abrir” apenas abre o aplicativo escolhido.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(query, { query = it.take(120) }, label = { Text("Buscar aplicativo") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("automation_app_search"))
        }
        if (appChoices.isEmpty()) item { Text("Nenhum aplicativo disponível para esta busca.", color = AuronColors.Muted) }
        items(appChoices, key = { "app:${it.packageName}" }) { app ->
            AutomationPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = app.packageName in state.settings.allowedPackages,
                        onCheckedChange = { allowed ->
                            val selected = if (allowed) state.settings.allowedPackages + app.packageName else state.settings.allowedPackages - app.packageName
                            onSave(state.settings.copy(allowedPackages = selected))
                        },
                        enabled = profileAvailable,
                        modifier = Modifier.testTag("automation_allow_${app.packageName}").semantics { contentDescription = "Permitir leitura de notificações de ${app.label}" },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                        Text("Permitir leitura das notificações", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                    }
                }
                OutlinedButton(onClick = { onOpenApp(app) }, enabled = profileAvailable && apps.any { it.packageName == app.packageName }, modifier = Modifier.testTag("automation_open_${app.packageName}")) { Text("Abrir ${app.label}") }
            }
        }
    }

    if (accessConsent && profileAvailable) {
        AlertDialog(
            onDismissRequest = { accessConsent = false },
            title = { Text("Permissão de notificações do Android") },
            text = { Text("Na próxima tela, você poderá conceder ou revogar o acesso do AURON. Isso permite ao Android disponibilizar suas notificações ao aplicativo. No AURON, a leitura também depende da ativação neste perfil e da lista de aplicativos autorizados. Você pode desativar a leitura a qualquer momento.") },
            confirmButton = { TextButton(onClick = { accessConsent = false; onAccessSettings() }, modifier = Modifier.testTag("automation_access_confirm")) { Text("Abrir configurações do Android") } },
            dismissButton = { TextButton(onClick = { accessConsent = false }) { Text("Cancelar") } },
        )
    }

    visibleNotifications.firstOrNull { it.id == replyTarget && it.isWhatsApp && it.canReply }?.let { notification ->
        key(state.profileId, notification.id, notification.revision) {
            ReplyEditor(notification, onDismiss = { replyTarget = null }) { text ->
                replyTarget = null
                onPrepareReply(notification.id, text)
            }
        }
    }
    state.pendingReply?.takeIf { canRead && it.profileId == state.profileId }?.let { draft ->
        var submitted by remember(draft.draftId) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onCancelReply,
            title = { Text("Confirmar envio da resposta?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Aplicativo: ${draft.appLabel}")
                    Text("Destinatário da notificação: ${draft.recipient}")
                    Text(draft.text, modifier = Modifier.testTag("automation_reply_preview"))
                    Text("O Android encaminhará esta resposta pela ação disponibilizada na notificação. Confira o destinatário e o texto antes de enviar.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                }
            },
            confirmButton = { TextButton(onClick = { submitted = true; onConfirmReply(draft.draftId) }, enabled = !submitted, modifier = Modifier.testTag("automation_send_confirm")) { Text("Confirmar e enviar") } },
            dismissButton = { TextButton(onClick = onCancelReply, enabled = !submitted, modifier = Modifier.testTag("automation_send_cancel")) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AutomationPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = AuronColors.Surface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun AutomationToggle(label: String, checked: Boolean, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.testTag(tag).semantics { contentDescription = label })
    }
}

@Composable
private fun ReplyEditor(notification: AuthorizedNotification, onDismiss: () -> Unit, onPrepare: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = runCatching { NotificationPolicy.replyText(text) }.isSuccess
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preparar resposta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${notification.appLabel} · ${notification.title}")
                OutlinedTextField(text, { text = it.take(NotificationPolicy.MAX_REPLY_LENGTH) }, label = { Text("Sua resposta") }, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("automation_reply_text"))
                Text("Você poderá revisar e confirmar o envio na próxima etapa.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
            }
        },
        confirmButton = { TextButton(onClick = { onPrepare(text) }, enabled = valid, modifier = Modifier.testTag("automation_prepare_confirm")) { Text("Revisar resposta") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
