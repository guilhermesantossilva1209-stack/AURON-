package br.com.auron.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.auron.domain.Period
import br.com.auron.domain.RoutineItem
import br.com.auron.domain.Task
import br.com.auron.domain.InputLimits
import br.com.auron.ui.theme.AuronColors
import br.com.auron.ui.theme.AuronTheme
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** The command center only summarizes persisted state; it invents no activity or AI status. */
@Composable
fun DashboardScreen(
    state: AuronUiState,
    onNavigate: (String) -> Unit,
    onVoice: () -> Unit,
    listening: Boolean,
    speaking: Boolean,
    onStopSpeaking: () -> Unit,
    onToggleRoutine: (RoutineItem) -> Unit,
    onToggleTask: (Task) -> Unit,
    onSendText: (String) -> Unit,
) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1_000)
        }
    }
    val locale = remember { Locale.forLanguageTag("pt-BR") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss", locale) }
    val greeting = when (now.hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    val pendingTasks = state.tasks.filter { it.completedAt == null }
        .sortedWith(compareByDescending<Task> { it.priority }.thenBy { it.dueDate ?: "9999-12-31" }.thenBy { it.createdAt })
    val morningItems = state.routineItems.filter { it.period == Period.MORNING }.sortedBy { it.orderIndex }
    val completedIds = state.routineCompletions.filter { it.day == state.day }.map { it.itemId }.toSet()
    val morningCompleted = morningItems.count { it.id in completedIds }
    val allRoutineCompleted = state.routineItems.count { it.id in completedIds }
    val progress = if (state.routineItems.isEmpty()) 0f else allRoutineCompleted.toFloat() / state.routineItems.size

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 780.dp
        val heroWide = maxWidth >= 600.dp
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (wide) 28.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("CENTRO DE COMANDO", style = MaterialTheme.typography.labelMedium, color = AuronColors.Gold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("$greeting, ${state.settings.userName.ifBlank { "comandante" }}.", style = if (wide) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(now.format(dateFormatter).replaceFirstChar { it.titlecase(locale) }, color = AuronColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                if (wide) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(now.format(timeFormatter), style = MaterialTheme.typography.headlineMedium, color = AuronColors.Gold)
                        Text("HORÁRIO DO DISPOSITIVO", style = MaterialTheme.typography.labelSmall, color = AuronColors.Muted)
                    }
                }
            }
            if (!wide) Text(now.format(timeFormatter), color = AuronColors.Gold, style = MaterialTheme.typography.titleMedium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AuronColors.Surface),
                border = BorderStroke(1.dp, AuronColors.Gold.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(26.dp),
            ) {
                if (heroWide) {
                    Row(Modifier.padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        HeroCopy(Modifier.weight(1f), listening, speaking, onVoice, onStopSpeaking, onSendText, state.busy, state.aiStatus)
                        Spacer(Modifier.width(20.dp))
                        AuronRobot(Modifier.width(220.dp).height(308.dp), motionEnabled = !state.settings.reducedMotion)
                    }
                } else {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AuronRobot(Modifier.width(180.dp).height(252.dp), motionEnabled = !state.settings.reducedMotion)
                        Spacer(Modifier.height(18.dp))
                        HeroCopy(Modifier.fillMaxWidth(), listening, speaking, onVoice, onStopSpeaking, onSendText, state.busy, state.aiStatus)
                    }
                }
            }

            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricCard(Modifier.weight(1f), "TAREFAS PENDENTES", pendingTasks.size.toString(), "Seu próximo movimento", Icons.Outlined.TaskAlt) { onNavigate("tasks") }
                    MetricCard(Modifier.weight(1f), "ROTINA DE HOJE", "$allRoutineCompleted / ${state.routineItems.size}", "Atividades concluídas", Icons.Outlined.CheckCircleOutline) { onNavigate("routine") }
                    MetricCard(Modifier.weight(1f), "HISTÓRICO LOCAL", state.messages.size.toString(), "Mensagens registradas", Icons.Outlined.ChatBubbleOutline) { onNavigate("chat") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(Modifier.weight(1f), "PENDENTES", pendingTasks.size.toString(), "Tarefas", Icons.Outlined.TaskAlt) { onNavigate("tasks") }
                    MetricCard(Modifier.weight(1f), "ROTINA HOJE", "$allRoutineCompleted / ${state.routineItems.size}", "Concluídas", Icons.Outlined.CheckCircleOutline) { onNavigate("routine") }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = AuronColors.Surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pequenos passos. Progresso real.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Text("${(progress * 100).toInt()}%", color = AuronColors.Gold, style = MaterialTheme.typography.titleLarge)
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = AuronColors.Gold, trackColor = AuronColors.Elevated)
                    Text("$allRoutineCompleted de ${state.routineItems.size} atividades da rotina concluídas hoje.", color = AuronColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
                    PrioritiesPanel(Modifier.weight(1f), pendingTasks.take(3), onToggleTask) { onNavigate("tasks") }
                    MorningPanel(Modifier.weight(1f), morningItems.take(4), completedIds, morningCompleted, morningItems.size, onToggleRoutine) { onNavigate("routine/morning") }
                }
            } else {
                PrioritiesPanel(Modifier.fillMaxWidth(), pendingTasks.take(3), onToggleTask) { onNavigate("tasks") }
                MorningPanel(Modifier.fillMaxWidth(), morningItems.take(4), completedIds, morningCompleted, morningItems.size, onToggleRoutine) { onNavigate("routine/morning") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Acesso rápido", style = MaterialTheme.typography.titleLarge)
                val actions = listOf(
                    QuickAction("Conversar", "Comandos e histórico", Icons.Outlined.ChatBubbleOutline, "chat"),
                    QuickAction("Minha rotina", "Manhã e noite", Icons.Outlined.WbSunny, "routine"),
                    QuickAction("Tarefas", "Organize suas prioridades", Icons.Outlined.TaskAlt, "tasks"),
                    QuickAction("Módulos", "Explore a evolução", Icons.Outlined.GridView, "modules"),
                )
                actions.chunked(if (wide) 4 else 2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { action -> QuickActionCard(Modifier.weight(1f), action) { onNavigate(action.destination) } }
                    }
                }
            }
            Text("AURON  /  SISTEMA DE INTELIGÊNCIA PESSOAL", style = MaterialTheme.typography.labelSmall, color = AuronColors.Muted, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun HeroCopy(modifier: Modifier, listening: Boolean, speaking: Boolean, onVoice: () -> Unit, onStopSpeaking: () -> Unit, onSendText: (String) -> Unit, busy: Boolean, aiStatus: String) {
    var command by rememberSaveable { mutableStateOf("") }
    fun sendCommand() {
        if (command.isNotBlank() && !busy) {
            onSendText(command.trim())
            command = ""
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).background(if (listening) AuronColors.Success else AuronColors.Gold, CircleShape))
            Text(if (listening) "MICROFONE ATIVO" else if (speaking) "RESPOSTA FALADA ATIVA" else aiStatus.uppercase(Locale.forLanguageTag("pt-BR")), style = MaterialTheme.typography.labelMedium, color = AuronColors.Gold)
        }
        Text(if (listening) "Estou ouvindo." else "Tudo começa\ncom um comando.", style = MaterialTheme.typography.headlineMedium)
        Text("Organize tarefas, acompanhe sua rotina e converse com o AURON. O estado da inteligência aparece acima; conexões e consentimentos ficam sob seu controle.", color = AuronColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onVoice, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 13.dp)) {
            Icon(if (listening) Icons.Outlined.MicOff else Icons.Outlined.Mic, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (listening) "Interromper escuta" else "Falar com AURON")
        }
        if (speaking) {
            OutlinedButton(onClick = onStopSpeaking) {
                Icon(Icons.Outlined.StopCircle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Parar resposta falada")
            }
        }
        OutlinedTextField(
            value = command,
            onValueChange = { if (it.length <= InputLimits.MESSAGE) command = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ou escreva um comando") },
            maxLines = 3,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { sendCommand() }),
            trailingIcon = { IconButton(onClick = { sendCommand() }, enabled = command.isNotBlank() && !busy) { Icon(Icons.Outlined.Send, "Enviar comando") } },
        )
    }
}

@Composable
private fun MetricCard(modifier: Modifier, title: String, value: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = AuronColors.Surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = AuronColors.Muted, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(4.dp))
                Icon(icon, null, Modifier.size(18.dp), tint = AuronColors.Gold)
            }
            Text(value, style = MaterialTheme.typography.headlineLarge)
            Text(detail, color = AuronColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PrioritiesPanel(modifier: Modifier, tasks: List<Task>, onToggle: (Task) -> Unit, onAll: () -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = AuronColors.Surface)) {
        Column(Modifier.padding(18.dp)) {
            PanelTitle("Suas prioridades", "Tarefas pendentes", onAll)
            if (tasks.isEmpty()) {
                Text("Sem tarefas pendentes. Abra espaço para o que importa.", color = AuronColors.Muted, modifier = Modifier.padding(vertical = 22.dp), style = MaterialTheme.typography.bodyMedium)
            }
            tasks.forEachIndexed { index, task ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = { onToggle(task) }, modifier = Modifier.semantics { contentDescription = "Concluir tarefa: ${task.title}" })
                    Column(Modifier.weight(1f)) {
                        Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        task.dueDate?.let { Text("Prazo: ${displayDate(it)}", color = AuronColors.Muted, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                if (index < tasks.lastIndex) HorizontalDivider(color = AuronColors.Elevated)
            }
            TextButton(onClick = onAll) { Text("Gerenciar tarefas"); Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun MorningPanel(modifier: Modifier, items: List<RoutineItem>, completed: Set<String>, completedCount: Int, total: Int, onToggle: (RoutineItem) -> Unit, onAll: () -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = AuronColors.Surface)) {
        Column(Modifier.padding(18.dp)) {
            PanelTitle("Ritual da manhã", "$completedCount de $total concluídos", onAll)
            if (items.isEmpty()) Text("Personalize sua manhã adicionando atividades à rotina.", color = AuronColors.Muted, modifier = Modifier.padding(vertical = 22.dp), style = MaterialTheme.typography.bodyMedium)
            items.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.id in completed, onCheckedChange = { onToggle(item) }, modifier = Modifier.semantics { contentDescription = "Atividade: ${item.title}" })
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, color = if (item.id in completed) AuronColors.Muted else AuronColors.Text, modifier = Modifier.weight(1f))
                }
                if (index < items.lastIndex) HorizontalDivider(color = AuronColors.Elevated)
            }
            TextButton(onClick = onAll) { Text("Ver rotina completa"); Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun PanelTitle(title: String, subtitle: String, onAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = AuronColors.Muted, modifier = Modifier.padding(top = 3.dp))
        }
        androidx.compose.material3.IconButton(onClick = onAll) { Icon(Icons.Outlined.NorthEast, contentDescription = "Abrir $title", tint = AuronColors.Gold, modifier = Modifier.size(20.dp)) }
    }
}

private data class QuickAction(val title: String, val detail: String, val icon: ImageVector, val destination: String)

@Composable
private fun QuickActionCard(modifier: Modifier, action: QuickAction, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = AuronColors.Surface), border = BorderStroke(1.dp, AuronColors.Elevated)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(action.icon, null, Modifier.size(24.dp), tint = AuronColors.Gold)
            Text(action.title, style = MaterialTheme.typography.titleMedium)
            Text(action.detail, style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
        }
    }
}

private fun displayDate(value: String): String = runCatching {
    java.time.LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(value)

@Preview(name = "Tablet landscape", widthDp = 1060, heightDp = 800, showBackground = true)
@Preview(name = "Compact", widthDp = 412, heightDp = 900, showBackground = true)
@Preview(name = "Larger text", widthDp = 720, heightDp = 1000, fontScale = 1.4f, showBackground = true)
@Composable
private fun DashboardPreview() {
    AuronTheme {
        DashboardScreen(AuronUiState(ready = true), {}, {}, false, false, {}, {}, {}, {})
    }
}
