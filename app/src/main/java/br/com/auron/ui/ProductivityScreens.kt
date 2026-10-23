package br.com.auron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.auron.domain.Period
import br.com.auron.domain.RoutineItem
import br.com.auron.domain.Task
import br.com.auron.domain.InputLimits
import br.com.auron.bible.isBibleReading
import br.com.auron.ui.theme.AuronColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun TasksScreen(
    state: AuronUiState,
    add: (String, String, Int, String?) -> Unit,
    update: (Task) -> Unit,
    toggle: (Task) -> Unit,
    delete: (String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("pending") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var deleting by remember { mutableStateOf<Task?>(null) }
    val filtered = state.tasks.filter {
        when (filter) { "pending" -> it.completedAt == null; "done" -> it.completedAt != null; else -> true }
    }.sortedWith(compareBy<Task> { it.completedAt != null }.thenByDescending { it.priority }.thenBy { it.dueDate ?: "9999" }.thenByDescending { it.createdAt })
    LazyColumn(Modifier.fillMaxSize().testTag("tasks_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeading("Tarefas & operações", "Uma intenção clara. Um próximo passo.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = { creating = true }, modifier = Modifier.testTag("task_add")) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova tarefa") }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == "pending", onClick = { filter = "pending" }, label = { Text("Pendentes") })
                FilterChip(selected = filter == "done", onClick = { filter = "done" }, label = { Text("Concluídas") })
                FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("Todas") })
            }
            Text("${filtered.size} tarefa(s) neste filtro", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
        }
        if (filtered.isEmpty()) item { EmptyState("Espaço para o próximo passo", if (filter == "done") "As tarefas concluídas aparecem aqui e no Histórico." else "Crie uma tarefa pelo botão acima ou diga: criar tarefa, seguido do título.") }
        items(filtered, key = { it.id }) { task ->
            TaskCard(task, state.day, { toggle(task) }, { editing = task }, { deleting = task })
        }
    }
    if (creating || editing != null) TaskEditor(editing, onDismiss = { creating = false; editing = null }) { title, notes, priority, date ->
        val original = editing
        if (original == null) add(title, notes, priority, date) else update(original.copy(title = title, notes = notes, priority = priority, dueDate = date))
        creating = false
        editing = null
    }
    deleting?.let { task -> ConfirmDialog("Excluir tarefa?", "“${task.title}” será removida dos registros locais.", "Excluir", { deleting = null }) { delete(task.id); deleting = null } }
}

@Composable
private fun TaskCard(task: Task, day: String, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(color = AuronColors.Surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = task.completedAt != null, onCheckedChange = { onToggle() }, modifier = Modifier.semantics { contentDescription = if (task.completedAt == null) "Concluir ${task.title}" else "Reabrir ${task.title}" })
                Column(Modifier.weight(1f).padding(top = 10.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, textDecoration = if (task.completedAt != null) TextDecoration.LineThrough else null, color = if (task.completedAt != null) AuronColors.Muted else AuronColors.Text)
                    if (task.notes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(task.notes, style = MaterialTheme.typography.bodyMedium, color = AuronColors.Muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("PRIORIDADE ${priorityName(task.priority).uppercase()}", color = if (task.priority == 2 && task.completedAt == null) AuronColors.Gold else AuronColors.Muted, style = MaterialTheme.typography.labelSmall)
                    task.dueDate?.let { due ->
                        Text("Prazo: ${displayDay(due)}${if (task.completedAt == null && due < day) " · atrasada" else ""}", style = MaterialTheme.typography.bodySmall, color = if (task.completedAt == null && due < day) AuronColors.Danger else AuronColors.Muted)
                    }
                    task.completedAt?.let { Text("Concluída em ${displayTimestamp(it)}", color = AuronColors.Success, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Editar") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Excluir ${task.title}", tint = AuronColors.Muted) }
            }
        }
    }
}

@Composable
private fun TaskEditor(task: Task?, onDismiss: () -> Unit, onSave: (String, String, Int, String?) -> Unit) {
    var title by rememberSaveable(task?.id) { mutableStateOf(task?.title ?: "") }
    var notes by rememberSaveable(task?.id) { mutableStateOf(task?.notes ?: "") }
    var priority by rememberSaveable(task?.id) { mutableIntStateOf(task?.priority ?: 1) }
    var date by rememberSaveable(task?.id) { mutableStateOf(task?.dueDate ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "Nova tarefa" else "Editar tarefa") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { if (it.length <= InputLimits.TITLE) title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth().testTag("task_title"), singleLine = true)
                OutlinedTextField(notes, { if (it.length <= 4000) notes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
                Text("Prioridade", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..2).forEach { value -> FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(priorityName(value)) }) }
                }
                OutlinedTextField(date, { if (it.length <= 10) date = it }, label = { Text("Prazo opcional") }, placeholder = { Text("AAAA-MM-DD") }, supportingText = { Text("Exemplo: 2026-12-31") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("task_due_date"))
                error?.let { Text(it, color = AuronColors.Danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    title.isBlank() -> "Informe um título."
                    date.isNotBlank() && (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || runCatching { LocalDate.parse(date) }.isFailure) -> "Use uma data válida no formato AAAA-MM-DD."
                    else -> null
                }
                if (error == null) onSave(title.trim(), notes.trim(), priority, date.takeIf { it.isNotBlank() })
            }, modifier = Modifier.testTag("task_save")) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun RoutineScreen(
    state: AuronUiState,
    period: Period,
    onPeriod: (Period) -> Unit,
    toggle: (RoutineItem) -> Unit,
    save: (RoutineItem) -> Unit,
    add: (String, Period) -> Unit,
    delete: (String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RoutineItem?>(null) }
    var deleting by remember { mutableStateOf<RoutineItem?>(null) }
    val items = state.routineItems.filter { it.period == period }.sortedBy { it.orderIndex }
    val completed = state.routineCompletions.filter { it.day == state.day }.map { it.itemId }.toSet()
    val count = items.count { it.id in completed }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeading("Ritmo do dia", "Rotinas editáveis · ${displayDay(state.day)}")
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = period == Period.MORNING, onClick = { onPeriod(Period.MORNING) }, label = { Text("Manhã") }, leadingIcon = { Icon(Icons.Outlined.LightMode, null, Modifier.size(16.dp)) }, modifier = Modifier.testTag("routine_morning"))
                FilterChip(selected = period == Period.NIGHT, onClick = { onPeriod(Period.NIGHT) }, label = { Text("Noite") }, leadingIcon = { Icon(Icons.Outlined.DarkMode, null, Modifier.size(16.dp)) }, modifier = Modifier.testTag("routine_night"))
            }
            Spacer(Modifier.height(12.dp))
            Text("$count de ${items.size} atividades concluídas", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { if (items.isEmpty()) 0f else count.toFloat() / items.size }, modifier = Modifier.fillMaxWidth(), color = AuronColors.Gold, trackColor = AuronColors.Elevated)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Adicionar atividade") }
        }
        if (items.isEmpty()) item { EmptyState("Sua rotina começa aqui", "Adicione as atividades que fazem sentido para ${if (period == Period.NIGHT) "encerrar" else "iniciar"} seu dia.") }
        items(items, key = { it.id }) { item ->
            Surface(color = AuronColors.Surface, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.id in completed, onCheckedChange = { toggle(item) }, modifier = Modifier.semantics { contentDescription = if (item.isBibleReading()) "Abrir leitura do dia: ${item.title}" else "${if (item.id in completed) "Desmarcar" else "Concluir"} ${item.title}" })
                    Text(item.title, modifier = Modifier.weight(1f).then(if (item.isBibleReading()) Modifier.clickable(onClickLabel = "Abrir leitura do dia") { toggle(item) }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically) else Modifier), style = MaterialTheme.typography.bodyLarge, color = if (item.id in completed) AuronColors.Muted else AuronColors.Text, textDecoration = if (item.id in completed) TextDecoration.LineThrough else null)
                    IconButton(onClick = { editing = item }) { Icon(Icons.Outlined.Edit, "Editar ${item.title}", Modifier.size(20.dp), tint = AuronColors.Muted) }
                    IconButton(onClick = { deleting = item }) { Icon(Icons.Outlined.DeleteOutline, "Excluir ${item.title}", Modifier.size(20.dp), tint = AuronColors.Muted) }
                }
            }
        }
        item { Text("Cada dia tem seu próprio progresso. Consulte os dias anteriores no Histórico.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted, modifier = Modifier.padding(top = 10.dp)) }
    }
    if (creating || editing != null) {
        RoutineEditor(editing?.title ?: "", onDismiss = { creating = false; editing = null }) { title ->
            editing?.let { save(it.copy(title = title)) } ?: add(title, period)
            creating = false
            editing = null
        }
    }
    deleting?.let { item -> ConfirmDialog("Excluir atividade?", "“${item.title}” será removida da rotina.", "Excluir", { deleting = null }) { delete(item.id); deleting = null } }
}

@Composable
private fun RoutineEditor(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.isEmpty()) "Nova atividade" else "Editar atividade") }, text = { OutlinedTextField(title, { if (it.length <= InputLimits.TITLE) title = it }, label = { Text("Atividade") }, modifier = Modifier.fillMaxWidth(), maxLines = 3) }, confirmButton = { TextButton(onClick = { onSave(title.trim()) }, enabled = title.isNotBlank()) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
internal fun HistoryScreen(state: AuronUiState, reopen: (Task) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("tasks") }
    val tasks = state.tasks.filter { it.completedAt != null }.sortedByDescending { it.completedAt }
    val days = state.routineCompletions.groupBy { it.day }.toSortedMap(compareByDescending { it })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeading("Histórico", "O que você concluiu, registrado no seu dispositivo.")
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == "tasks", onClick = { tab = "tasks" }, label = { Text("Tarefas") })
                FilterChip(selected = tab == "routine", onClick = { tab = "routine" }, label = { Text("Rotinas por dia") })
            }
        }
        if (tab == "tasks") {
            if (tasks.isEmpty()) item { EmptyState("Nenhuma tarefa concluída", "Ao concluir uma tarefa, o título e a data ficarão disponíveis aqui enquanto o registro for mantido.") }
            items(tasks, key = { it.id }) { task ->
                Surface(color = AuronColors.Surface, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = AuronColors.Success, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Concluída em ${displayTimestamp(requireNotNull(task.completedAt))}", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                        TextButton(onClick = { reopen(task) }, modifier = Modifier.align(Alignment.End)) { Text("Reabrir tarefa") }
                    }
                }
            }
        } else {
            if (days.isEmpty()) item { EmptyState("Seu primeiro registro está por vir", "Marque uma atividade da rotina para registrar a conclusão de hoje.") }
            days.forEach { (day, completions) ->
                item(key = day) {
                    Surface(color = AuronColors.Surface, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(displayDay(day), style = MaterialTheme.typography.titleMedium, color = AuronColors.Gold)
                            Text("${completions.size} atividade(s) concluída(s)", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
                            completions.sortedBy { it.completedAt }.forEach { completion ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Outlined.Check, null, tint = AuronColors.Success, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(completion.title, style = MaterialTheme.typography.bodyMedium)
                                        Text(if (completion.period == Period.MORNING) "Manhã" else "Noite", style = MaterialTheme.typography.labelSmall, color = AuronColors.Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScreenHeading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AuronColors.Muted)
}

@Composable
internal fun EmptyState(title: String, description: String) {
    Surface(color = AuronColors.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Column(Modifier.padding(24.dp)) {
            Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = AuronColors.Gold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = AuronColors.Muted)
        }
    }
}

@Composable
internal fun ConfirmDialog(title: String, message: String, confirm: String, dismiss: () -> Unit, action: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = action) { Text(confirm, color = AuronColors.Danger) } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

private fun priorityName(priority: Int) = when (priority) { 0 -> "Baixa"; 2 -> "Alta"; else -> "Normal" }
internal fun displayDay(day: String): String = runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(day)
private fun displayTimestamp(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))
