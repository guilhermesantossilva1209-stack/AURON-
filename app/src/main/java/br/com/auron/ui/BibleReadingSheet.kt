package br.com.auron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import br.com.auron.bible.BibleCatalog
import br.com.auron.bible.BibleVerse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleReadingSheet(day: String, verses: List<BibleVerse>, completed: Boolean, onComplete: () -> Unit, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text("Leitura bíblica • $day", style = MaterialTheme.typography.headlineSmall)
            Text("Cinco versículos do dia · disponível offline", style = MaterialTheme.typography.bodyMedium)
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                items(verses, key = { it.id }) { verse ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(verse.reference, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            Text(verse.text, style = MaterialTheme.typography.bodyLarge)
                            Text("Reflexão • ${verse.reflection}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    Text(BibleCatalog.CREDIT, style = MaterialTheme.typography.bodySmall)
                    Text("Reflexões editoriais do AURON, separadas do texto bíblico. Seleção de 35 versículos em ciclo de sete dias.", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { uri.openUri(BibleCatalog.SOURCE) }) { Text("Fonte e autores") }
                        TextButton(onClick = { uri.openUri(BibleCatalog.LICENSE) }) { Text("Licença") }
                    }
                }
            }
            HorizontalDivider()
            Text("Conclusão da tarefa", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text(if (completed) "Leitura concluída. Você pode reler sempre que quiser." else "A atividade só será concluída ao confirmar abaixo.")
            Button(onClick = onComplete, enabled = !completed && verses.size == 5, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(if (completed) "Leitura concluída" else "Concluir leitura")
            }
        }
    }
}
