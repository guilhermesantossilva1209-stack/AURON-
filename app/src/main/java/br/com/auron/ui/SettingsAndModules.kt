package br.com.auron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.auron.domain.AppSettings
import br.com.auron.auth.AuthState
import br.com.auron.ui.theme.AuronColors
import br.com.auron.voice.VoiceState
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    save: (AppSettings) -> Unit,
    clearChat: () -> Unit,
    clearAll: () -> Unit,
    export: () -> Unit,
    authState: AuthState,
    profileLabel: String,
    aiStatus: String,
    login: () -> Unit,
    logout: () -> Unit,
    importLegacy: () -> Unit,
    connections: () -> Unit,
    openMemory: () -> Unit,
    voiceState: VoiceState,
    previewVoice: (AppSettings) -> Unit,
    refreshVoices: () -> Unit,
    stopVoice: () -> Unit,
    openCloud: () -> Unit,
    openAutomation: () -> Unit = {},
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var confirming by remember { mutableStateOf<String?>(null) }
    val dirty = draft != settings
    LazyColumn(Modifier.fillMaxSize().testTag("screen_settings"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenHeading("Do seu jeito", "Preferências, voz e controle dos seus registros.") }
        item {
            SettingsPanel("Perfil & conexão") {
                Text(profileLabel, style = MaterialTheme.typography.titleMedium, color = AuronColors.Gold)
                if (!authState.localMode) authState.session?.let { Text(it.email, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(8.dp))
                Text("Cada conta usa registros separados neste dispositivo. Entrar na conta não sincroniza automaticamente seus dados com a nuvem.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                Spacer(Modifier.height(12.dp))
                if (authState.localMode || authState.session == null) {
                    OutlinedButton(onClick = login, enabled = !authState.busy, modifier = Modifier.fillMaxWidth()) { Text("Entrar ou criar conta") }
                } else {
                    OutlinedButton(onClick = { confirming = "import" }, enabled = !authState.busy, modifier = Modifier.fillMaxWidth()) { Text("Copiar dados do perfil local") }
                    TextButton(onClick = { confirming = "logout" }, enabled = !authState.busy, modifier = Modifier.fillMaxWidth()) { Text("Sair da conta") }
                }
                OutlinedButton(onClick = connections, modifier = Modifier.fillMaxWidth()) { Text("Configurar conexões") }
                if (!authState.localMode && authState.session != null) {
                    OutlinedButton(onClick = openCloud, modifier = Modifier.fillMaxWidth()) { Text("Cópia privada no Supabase") }
                }
                authState.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AuronColors.Success) }
                authState.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AuronColors.Danger) }
            }
        }
        item {
            SettingsPanel("Automações Android") {
                Text("Abrir aplicativos, consultar notificações permitidas e confirmar respostas disponíveis. Você controla o acesso pelo Android.", style = MaterialTheme.typography.bodyMedium, color = AuronColors.Muted)
                OutlinedButton(onClick = openAutomation, modifier = Modifier.fillMaxWidth()) { Text("Configurar automações e permissões") }
            }
        }
        item {
            SettingsPanel("Identidade") {
                OutlinedTextField(value = draft.userName, onValueChange = { if (it.length <= 60) draft = draft.copy(userName = it) }, label = { Text("Como devo chamar você?") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("settings_name"), supportingText = { Text("Seu nome ou um tratamento, como comandante.") })
            }
        }
        item {
            SettingsPanel("Voz") {
                SettingToggle("Falar respostas", "Usa uma voz pt-BR instalada no dispositivo. A disponibilidade depende do Android.", draft.speakReplies) { draft = draft.copy(speakReplies = it) }
                Spacer(Modifier.height(16.dp))
                VoiceSettings(draft, voiceState, { draft = it }, previewVoice, refreshVoices, stopVoice)
                HorizontalDivider(color = AuronColors.Elevated, modifier = Modifier.padding(vertical = 12.dp))
                SettingToggle("Permitir reconhecimento online", "Consentimento opcional: quando o reconhecimento local não estiver disponível, o serviço de voz do Android poderá enviar seu áudio ao provedor configurado no aparelho. Desativado por padrão.", draft.allowOnlineSpeech) { draft = draft.copy(allowOnlineSpeech = it) }
                Spacer(Modifier.height(12.dp))
                Text("AURON só escuta após seu toque, com o aplicativo aberto. A permissão de microfone é solicitada nesse momento.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
            }
        }
        item {
            SettingsPanel("Inteligência & consentimento") {
                Text("Estado: $aiStatus", style = MaterialTheme.typography.bodyMedium, color = AuronColors.Gold)
                Spacer(Modifier.height(14.dp))
                SettingToggle("Permitir IA remota", "Autoriza enviar as próximas mensagens ao backend HTTPS configurado e ao provedor de IA dele. Requer conexão e sessão válida ou acesso pessoal do servidor no modo local; custos dependem do provedor configurado. Nenhuma API é contratada pelo aplicativo.", draft.useRemoteAi) { draft = draft.copy(useRemoteAi = it) }
                Spacer(Modifier.height(18.dp))
                SettingToggle("Compartilhar histórico com a IA", "Autoriza incluir um trecho recente desta conversa nas próximas solicitações remotas. Desativado, somente a mensagem atual e as preferências de personalidade são utilizadas.", draft.shareHistoryWithAi) { draft = draft.copy(shareHistoryWithAi = it) }
                Spacer(Modifier.height(18.dp))
                SettingToggle("Ativar memória local", "Permite criar registros explícitos na tela Memória. Desativar não apaga registros existentes e interrompe o compartilhamento de memórias.", draft.memoryEnabled) { draft = draft.copy(memoryEnabled = it, shareMemoryWithAi = if (it) draft.shareMemoryWithAi else false) }
                Spacer(Modifier.height(18.dp))
                SettingToggle("Compartilhar memórias com a IA", "Autoriza enviar conteúdo dos registros da tela Memória nas próximas solicitações remotas. Leia e edite esses registros antes de autorizar.", draft.shareMemoryWithAi, enabled = draft.memoryEnabled) { draft = draft.copy(shareMemoryWithAi = it) }
                TextButton(onClick = openMemory) { Text("Abrir registros de memória") }
                Text("Nenhuma dessas opções executa comandos recebidos do modelo. Alterações valem após salvar as preferências.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
            }
        }
        item {
            SettingsPanel("Aparência & movimento") {
                SettingToggle("Mostrar abertura", "Uma apresentação breve e pulável ao iniciar o aplicativo.", draft.showIntro) { draft = draft.copy(showIntro = it) }
                Spacer(Modifier.height(18.dp))
                SettingToggle("Reduzir movimento", "Desativa a abertura e os movimentos decorativos do robô. A preferência de animações do Android também é respeitada.", draft.reducedMotion) { draft = draft.copy(reducedMotion = it) }
            }
        }
        item {
            SettingsPanel("Personalidade") {
                Text("Preferências de estilo enviadas à IA quando ela estiver configurada e autorizada. O modo demonstrativo continua usando comandos e respostas programadas.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                Spacer(Modifier.height(16.dp))
                PercentageSlider("Humor", draft.humor) { draft = draft.copy(humor = it) }
                PercentageSlider("Sarcasmo leve", draft.sarcasm) { draft = draft.copy(sarcasm = it) }
                PercentageSlider("Formalidade", draft.formality) { draft = draft.copy(formality = it) }
                PercentageSlider("Proatividade", draft.proactivity) { draft = draft.copy(proactivity = it) }
                Text("Avisos de notificações autorizadas são configurados em Automações. Lembretes agendados continuam planejados.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
            }
        }
        item {
            Button(onClick = { save(draft.copy(userName = draft.userName.trim())) }, enabled = dirty && draft.userName.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("settings_save")) { Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar preferências") }
            Text(if (dirty) "Há alterações ainda não salvas." else "Preferências sincronizadas com o armazenamento local.", color = AuronColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            SettingsPanel("Privacidade & dados") {
                Text("Os registros ficam no armazenamento privado deste perfil, com backup automático desativado. Memórias e credenciais de sessão usam criptografia com Android Keystore. Tarefas, rotinas e conversas não têm criptografia adicional; o perfil local não exige senha.", style = MaterialTheme.typography.bodyMedium, color = AuronColors.Muted)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = export, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Exportar meus dados") }
                Text("Arquivo JSON legível contendo também conversas e memórias. Você escolhe onde salvar. Importação de arquivos JSON ainda não disponível.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { confirming = "chat" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Limpar conversas") }
                TextButton(onClick = { confirming = "all" }, modifier = Modifier.fillMaxWidth()) { Text("Apagar todos os dados locais", color = AuronColors.Danger) }
            }
        }
        item {
            Text("AURON · v0.2.1 · Sistema de inteligência pessoal", style = MaterialTheme.typography.labelLarge, color = AuronColors.Gold)
            Spacer(Modifier.height(6.dp))
            Text("A disponibilidade de conta e IA depende dos servidores configurados. Publicação social e execução de operações financeiras não estão implementadas.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
        }
    }
    when (confirming) {
        "chat" -> ConfirmDialog("Limpar conversas?", "Todo o histórico de mensagens e transcrições será apagado deste dispositivo. Esta ação não pode ser desfeita.", "Limpar", { confirming = null }) { clearChat(); confirming = null }
        "all" -> ConfirmDialog("Apagar dados deste perfil?", "Tarefas, atividades da rotina, leituras, conclusões, memórias, conversas e preferências deste perfil serão apagados. Outros perfis não serão apagados. A rotina inicial não será recriada automaticamente. Exporte os registros antes, se quiser mantê-los.", "Apagar tudo", { confirming = null }) { clearAll(); confirming = null }
        "logout" -> ConfirmDialog("Sair da conta?", "A sessão será encerrada e a tela de login será aberta. Os dados desta conta continuam separados neste dispositivo para um próximo acesso.", "Sair", { confirming = null }) { logout(); confirming = null }
        "import" -> ConfirmDialog("Copiar registros locais?", "Uma cópia das tarefas, rotinas, conversas, leituras e memórias do perfil local será adicionada a esta conta no dispositivo. O perfil local será preservado e registros existentes não serão sobrescritos. As preferências não serão copiadas. A cópia é feita uma vez por perfil.", "Copiar para esta conta", { confirming = null }) { importLegacy(); confirming = null }
    }
}

@Composable
private fun SettingsPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = AuronColors.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = change, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun PercentageSlider(title: String, value: Int, change: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text("$value%", color = AuronColors.Gold, style = MaterialTheme.typography.labelLarge)
    }
    Slider(value = value.toFloat(), onValueChange = { change(it.roundToInt()) }, valueRange = 0f..100f, steps = 19, modifier = Modifier.semantics { contentDescription = title })
}

private data class PlannedModule(val name: String, val tag: String, val icon: ImageVector, val detail: String)
private val plannedModules = listOf(
    PlannedModule("Estudos", "FASE 4", Icons.Outlined.School, "Ciclos prolongados de aprendizagem, foco e revisões. Prioridade inicial: social media e edição audiovisual."),
    PlannedModule("Clau Estética", "FASE 4", Icons.Outlined.AutoAwesome, "Calendário editorial, campanhas, roteiros e métricas. APIs oficiais de redes sociais poderão ser integradas futuramente."),
    PlannedModule("Editor audiovisual premium", "FASE 4", Icons.Outlined.Movie, "Briefing, storyboard e etapas de edição. Preparação para ferramentas como Remotion; sem análise ou renderização de vídeos nesta versão."),
    PlannedModule("Finanças pessoais", "FASE 4", Icons.Outlined.AccountBalanceWallet, "Receitas, despesas, orçamento e cálculos verificáveis. Os registros financeiros ainda não estão disponíveis."),
    PlannedModule("Registro de operações", "FASE 4", Icons.Outlined.QueryStats, "Diário de sessões, WIN/LOSS e análise de risco. Somente registro e cálculo; nenhuma execução automática de operações."),
    PlannedModule("Espaço pessoal", "FASE 4", Icons.Outlined.SelfImprovement, "Diário, emoções e exercícios de respiração. Recursos de apoio que não substituem acompanhamento profissional."),
    PlannedModule("Espiritualidade ampliada", "FASE 4 · OPCIONAL", Icons.Outlined.MenuBook, "Diário de orações e plano de leitura completo ainda planejados. Os cinco versículos diários já estão vinculados à rotina matinal."),
)

@Composable
internal fun ModulesScreen(navigate: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("screen_modules"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            ScreenHeading("Um sistema. Novas possibilidades.", "A fundação está em construção contínua. Veja o que já funciona e o que vem depois.")
            Spacer(Modifier.height(20.dp))
            Text("DISPONÍVEL NESTA VERSÃO", style = MaterialTheme.typography.labelMedium, color = AuronColors.Gold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { navigate("chat") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text("Conversa") }
                OutlinedButton(onClick = { navigate("routine") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text("Rotina") }
                OutlinedButton(onClick = { navigate("tasks") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text("Tarefas") }
            }
            OutlinedButton(onClick = { navigate("memory") }, modifier = Modifier.fillMaxWidth()) { Text("Memória sob seu controle") }
            OutlinedButton(onClick = { navigate("automation") }, modifier = Modifier.fillMaxWidth()) { Text("Automações Android e notificações") }
            Text("Leitura bíblica disponível pela atividade da rotina matinal. IA remota e conta exigem configuração em Configurações.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
            Spacer(Modifier.height(22.dp))
            Text("MAPA DE EVOLUÇÃO · AINDA NÃO IMPLEMENTADO", style = MaterialTheme.typography.labelMedium, color = AuronColors.Muted)
        }
        items(plannedModules, key = { it.name }) { module ->
            Surface(color = AuronColors.Surface, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
                    Icon(module.icon, null, tint = AuronColors.Gold, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(module.tag + " · PLANEJADO", style = MaterialTheme.typography.labelSmall, color = AuronColors.Muted)
                        Spacer(Modifier.height(5.dp))
                        Text(module.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(module.detail, color = AuronColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
