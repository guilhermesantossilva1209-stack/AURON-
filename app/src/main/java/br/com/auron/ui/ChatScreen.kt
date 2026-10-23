package br.com.auron.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.auron.ui.theme.AuronColors
import br.com.auron.voice.VoiceState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sin

@Composable
internal fun ChatScreen(
    state: AuronUiState,
    voiceState: VoiceState,
    onSend: (String, String) -> Unit,
    onVoice: () -> Unit,
    onStopSpeaking: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    fun send() {
        if (input.isNotBlank() && !state.busy) {
            onSend(input.trim(), "text")
            input = ""
        }
    }
    LaunchedEffect(state.messages.size, state.busy) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Surface(color = AuronColors.Elevated, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = AuronColors.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("${state.aiStatus}. ${if (state.settings.useRemoteAi) "O envio de histórico e memórias depende dos consentimentos em Configurações." else "Modo demonstrativo: comandos locais e respostas programadas, sem modelo de IA."}", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
            }
        }
        if (state.messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.Forum, null, tint = AuronColors.Gold, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(20.dp))
                Text("Às suas ordens, ${state.settings.userName}.", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Organize o dia com comandos locais por texto ou voz.", style = MaterialTheme.typography.bodyMedium, color = AuronColors.Muted)
                Spacer(Modifier.height(24.dp))
                listOf("Bom dia, AURON.", "O que falta fazer?", "Criar tarefa: estudar edição", "Ajuda").forEach { command ->
                    OutlinedButton(onClick = { onSend(command, "text") }, enabled = !state.busy, modifier = Modifier.padding(bottom = 4.dp)) { Text(command) }
                }
            }
        } else LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("chat_history"), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(state.messages, key = { it.id }) { message ->
                val isUser = message.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (isUser) AuronColors.Elevated else AuronColors.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(0.92f)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isUser) "VOCÊ" else when (message.source) { "demo" -> "AURON · DEMO"; "ai" -> "AURON · IA"; "error" -> "AVISO DO SISTEMA"; else -> "AURON · LOCAL" }, style = MaterialTheme.typography.labelSmall, color = if (isUser) AuronColors.Muted else AuronColors.Gold, fontWeight = FontWeight.Bold)
                                if (message.source == "voice") {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Outlined.Mic, "Enviado por voz", modifier = Modifier.size(14.dp), tint = AuronColors.Muted)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.text.selection.SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyLarge) }
                            Spacer(Modifier.height(8.dp))
                            Text(chatTime(message.createdAt), style = MaterialTheme.typography.labelSmall, color = AuronColors.Muted, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }
            if (state.busy) item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Processando solicitação…", color = AuronColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (voiceState.listening) {
            val gold = AuronColors.Gold
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(94.dp).height(26.dp)) {
                    repeat(15) { i ->
                        val amplitude = 0.12f + voiceState.level.coerceIn(0f, 1f) * (0.3f + 0.6f * kotlin.math.abs(sin(i * 0.9f)))
                        val x = (i + 0.5f) * size.width / 15
                        drawLine(gold, Offset(x, size.height * (1 - amplitude) / 2), Offset(x, size.height * (1 + amplitude) / 2), strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text("Ouvindo em português…", color = AuronColors.Gold, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (voiceState.speaking) TextButton(onClick = onStopSpeaking) { Icon(Icons.Outlined.StopCircle, null); Spacer(Modifier.width(8.dp)); Text("Interromper resposta falada") }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 6.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input, onValueChange = { if (it.length <= 4000) input = it },
                modifier = Modifier.weight(1f).testTag("chat_input"),
                label = { Text("Fale com o AURON") },
                placeholder = { Text("Digite um comando…") },
                maxLines = 4, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onVoice, modifier = Modifier.size(52.dp), enabled = !state.busy || voiceState.listening) { Icon(if (voiceState.listening) Icons.Outlined.StopCircle else Icons.Outlined.Mic, if (voiceState.listening) "Parar escuta" else "Iniciar escuta", tint = AuronColors.Gold) }
            FilledIconButton(onClick = { send() }, enabled = input.isNotBlank() && !state.busy, modifier = Modifier.size(52.dp).testTag("chat_send")) { Icon(Icons.Outlined.Send, "Enviar mensagem") }
        }
        Text("A escuta começa apenas ao tocar no microfone.", color = AuronColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 10.dp))
    }
}

private fun chatTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"))
