package br.com.auron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.auron.cloud.CloudUiState
import br.com.auron.ui.theme.AuronColors

/** Account-scoped state and callbacks are supplied by the ViewModel. */
@Composable
fun CloudBackupScreen(
    state: CloudUiState,
    onInspect: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var consent by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Cópia privada na nuvem", style = MaterialTheme.typography.headlineSmall)
        Text("Supabase · conta do proprietário", color = AuronColors.Gold)
        Text("As rotinas continuam funcionando no dispositivo. Esta tela consulta, salva ou exclui uma cópia manual. Não há sincronização automática nem restauração sobre seus registros locais.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onInspect, enabled = !state.loading, modifier = Modifier.fillMaxWidth().testTag("cloud_inspect")) { Text("Consultar cópia na nuvem") }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = AuronColors.Danger) }
        state.notice?.let { Text(it, color = AuronColors.Success) }
        if (state.inspected) {
            val snapshot = state.snapshot
            if (snapshot == null) Text("Não existe cópia salva para esta conta.")
            else {
                Text("Última cópia · ${snapshot.updatedAt}", style = MaterialTheme.typography.titleMedium)
                Text("Revisão ${snapshot.revision}", style = MaterialTheme.typography.bodySmall)
                val labels = mapOf("tasks" to "Tarefas", "routineItems" to "Atividades da rotina", "routineCompletions" to "Conclusões registradas", "messages" to "Mensagens", "memories" to "Memórias", "readings" to "Leituras diárias")
                labels.forEach { (key, label) -> Text("$label: ${snapshot.counts[key] ?: 0}", style = MaterialTheme.typography.bodyMedium) }
                Text("Perfil e preferências também estão incluídos na cópia.", style = MaterialTheme.typography.bodySmall)
            }
        } else Text("Consulte primeiro para verificar o que já está armazenado antes de qualquer alteração.", color = AuronColors.Muted)
        HorizontalDivider()
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !state.loading, modifier = Modifier.testTag("cloud_consent"))
            Text("Autorizo enviar tarefas, rotinas, histórico, preferências, conversas e memórias deste perfil ao meu projeto Supabase. As memórias serão legíveis no banco para administradores autorizados; a criptografia local do Android não acompanha essa cópia.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { confirmation = "upload" }, enabled = state.inspected && consent && !state.loading, modifier = Modifier.fillMaxWidth().testTag("cloud_upload")) { Text(if (state.snapshot == null) "Salvar cópia na nuvem" else "Substituir cópia na nuvem") }
        if (state.snapshot != null) TextButton(onClick = { confirmation = "delete" }, enabled = state.inspected && !state.loading, modifier = Modifier.fillMaxWidth().testTag("cloud_delete")) { Text("Excluir somente a cópia na nuvem", color = AuronColors.Danger) }
        Text("O limite é 1 MiB por cópia. Cotas, região e retenção do serviço dependem do seu projeto Supabase; nenhum plano é contratado pelo aplicativo.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (action == "delete") "Excluir a cópia remota?" else "Enviar os registros deste perfil?") },
            text = { Text(if (action == "delete") "Apenas a cópia no Supabase será excluída. Os dados do aplicativo continuarão no tablet. Não será possível desfazer esta exclusão pela tela." else "Esta operação envia também memórias e conversas. Se houver uma cópia remota, ela será substituída pela versão atual deste dispositivo.") },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                if (state.inspected && !state.loading) {
                    if (action == "delete" && state.snapshot != null) onDelete()
                    else if (action == "upload" && consent) onUpload()
                }
            }) { Text(if (action == "delete") "Excluir cópia" else "Confirmar envio") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } },
        )
    }
}
