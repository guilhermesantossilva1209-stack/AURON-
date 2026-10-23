package br.com.auron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.auron.domain.InputLimits
import br.com.auron.domain.MemoryRecord
import br.com.auron.ui.theme.AuronColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun MemoryScreen(
    state: AuronUiState,
    enableMemory: () -> Unit,
    save: (String?, String, String) -> Unit,
    delete: (String) -> Unit,
    openSettings: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryRecord?>(null) }
    var deleting by remember { mutableStateOf<MemoryRecord?>(null) }
    var query by remember { mutableStateOf("") }
    val records = state.memories.filter { query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true) }.sortedByDescending { it.updatedAt }
    LazyColumn(Modifier.fillMaxSize().testTag("screen_memory"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            ScreenHeading("Memória sob seu controle", "Registre apenas o que deseja que o AURON possa lembrar neste perfil.")
            Spacer(Modifier.height(16.dp))
            Surface(color = AuronColors.Surface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, null, tint = AuronColors.Gold)
                        Spacer(Modifier.width(10.dp))
                        Text(if (state.settings.memoryEnabled) "Memória local ativada" else "Memória local desativada", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Você cria, corrige e apaga cada registro. O conteúdo é cifrado no dispositivo. Não há extração automática de lembranças das conversas.", style = MaterialTheme.typography.bodyMedium, color = AuronColors.Muted)
                    if (!state.settings.memoryEnabled) {
                        Text("Ativar permite salvar novos registros neste perfil. Os registros existentes permanecem até que você os exclua. O envio para IA exige outro consentimento.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                        Button(onClick = enableMemory, enabled = !state.busy, modifier = Modifier.testTag("memory_enable")) { Text("Ativar memória neste perfil") }
                    } else {
                        Text(if (state.settings.shareMemoryWithAi) "Compartilhamento com IA autorizado nas configurações. Depende de IA remota ativada e configurada." else "O envio dessas memórias à IA está desativado.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                        TextButton(onClick = openSettings) { Text("Gerenciar consentimentos") }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { creating = true }, enabled = state.settings.memoryEnabled && !state.busy, modifier = Modifier.testTag("memory_add")) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova memória") }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(query, { query = it.take(InputLimits.TITLE) }, label = { Text("Buscar nos registros") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (records.isEmpty()) item { EmptyState(if (query.isBlank()) "Nenhuma memória cadastrada" else "Nenhum registro encontrado", if (query.isBlank()) "Preferências, objetivos e contexto dos seus projetos podem ser guardados aqui por sua escolha." else "Experimente outra palavra da descrição ou do título.") }
        items(records, key = { it.id }) { memory ->
            Surface(color = AuronColors.Surface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(memory.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(memory.content, style = MaterialTheme.typography.bodyMedium, color = AuronColors.Muted)
                    Spacer(Modifier.height(12.dp))
                    Text("Atualizada em ${Instant.ofEpochMilli(memory.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))}", style = MaterialTheme.typography.labelSmall, color = AuronColors.Muted)
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = { editing = memory }, enabled = state.settings.memoryEnabled && !state.busy) { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Editar") }
                        IconButton(onClick = { deleting = memory }, enabled = !state.busy) { Icon(Icons.Outlined.DeleteOutline, "Excluir memória ${memory.title}") }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        MemoryEditor(editing, onDismiss = { creating = false; editing = null }) { title, content ->
            save(editing?.id, title, content)
            creating = false
            editing = null
        }
    }
    deleting?.let { memory -> ConfirmDialog("Excluir memória?", "“${memory.title}” será excluída deste perfil. Excluir aqui não remove cópias que você já tenha exportado ou enviado a um serviço externo.", "Excluir", { deleting = null }) { delete(memory.id); deleting = null } }
}

@Composable
private fun MemoryEditor(memory: MemoryRecord?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(memory?.id) { mutableStateOf(memory?.title ?: "") }
    var content by remember(memory?.id) { mutableStateOf(memory?.content ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (memory == null) "Nova memória" else "Corrigir memória") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { if (it.length <= InputLimits.TITLE) title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth().testTag("memory_title"), singleLine = true)
                OutlinedTextField(content, { if (it.length <= 4000) content = it }, label = { Text("O que deseja registrar?") }, modifier = Modifier.fillMaxWidth().testTag("memory_content"), minLines = 4, maxLines = 10)
                Text("Somente este registro será salvo. Você pode editá-lo ou apagá-lo depois.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title.trim(), content.trim()) }, enabled = title.isNotBlank() && content.isNotBlank(), modifier = Modifier.testTag("memory_save")) { Text("Salvar memória") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
