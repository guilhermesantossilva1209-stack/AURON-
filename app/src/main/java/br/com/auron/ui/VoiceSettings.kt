package br.com.auron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.auron.domain.AppSettings
import br.com.auron.ui.theme.AuronColors
import br.com.auron.voice.VoiceState
import java.util.Locale

/** Edits the settings draft; only the enclosing screen's Save persists changes. */
@Composable
internal fun VoiceSettings(
    settings: AppSettings,
    voiceState: VoiceState,
    onChange: (AppSettings) -> Unit,
    onPreview: (AppSettings) -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onRefresh() }
    DisposableEffect(Unit) { onDispose { onStop() } }
    val selected = voiceState.voiceOptions.firstOrNull {
        it.name == settings.speechVoiceName && it.engine == settings.speechVoiceEngine
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Voz brasileira offline", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ouça as opções e escolha a voz masculina que preferir. O Android não informa o gênero de forma confiável; as vozes disponíveis dependem do mecanismo instalado.",
            style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted,
        )
        Box {
            OutlinedButton(
                onClick = { choosing = true },
                enabled = !voiceState.loadingVoices,
                modifier = Modifier.fillMaxWidth().testTag("voice_choose"),
            ) {
                Text(when {
                    settings.speechVoiceName.isBlank() -> "Automática · português brasileiro offline"
                    selected != null -> selected.name
                    else -> "Voz salva indisponível · escolher outra"
                })
            }
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                DropdownMenuItem(
                    text = { Text("Automática · offline pt-BR") },
                    onClick = { onChange(settings.copy(speechVoiceName = "", speechVoiceEngine = "")); choosing = false },
                )
                voiceState.voiceOptions.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Column { Text("Voz ${index + 1}"); Text(option.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                        onClick = { onChange(settings.copy(speechVoiceName = option.name, speechVoiceEngine = option.engine)); choosing = false },
                    )
                }
            }
        }
        if (voiceState.loadingVoices) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Consultando vozes instaladas…", style = MaterialTheme.typography.bodySmall)
        } else if (voiceState.voiceOptions.isEmpty()) {
            Text("Nenhuma voz offline pt-BR disponível. Nas configurações de texto para fala do Android, instale os dados de Português (Brasil) e atualize a lista.", color = AuronColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("Velocidade · ${decimal(settings.speechRate)}×", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.speechRate.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f,
            onValueChange = { onChange(settings.copy(speechRate = it)) }, valueRange = 0.5f..2f, steps = 14,
            modifier = Modifier.semantics { contentDescription = "Velocidade de fala" },
        )
        Text("Tonalidade · ${decimal(settings.speechPitch)}×", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.speechPitch.takeIf { it.isFinite() }?.coerceIn(0.7f, 1.3f) ?: 1f,
            onValueChange = { onChange(settings.copy(speechPitch = it)) }, valueRange = 0.7f..1.3f, steps = 5,
            modifier = Modifier.semantics { contentDescription = "Tonalidade da voz" },
        )
        Text("Valores menores deixam o tom mais grave; ajustes suaves preservam a clareza. A tonalidade não muda a identidade da voz.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onPreview(settings) },
                enabled = !voiceState.loadingVoices && voiceState.ttsAvailable == true,
                modifier = Modifier.weight(1f).testTag("voice_preview"),
            ) { Text("Testar voz") }
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Interromper") }
        }
        TextButton(onClick = onRefresh, enabled = !voiceState.loadingVoices) { Text("Atualizar vozes instaladas") }
        voiceState.voiceNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AuronColors.Gold) }
        voiceState.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AuronColors.Danger) }
        Text("O teste usa estes ajustes sem salvá-los. Toque em Salvar preferências para manter a escolha neste perfil.", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
    }
}

private fun decimal(value: Float): String = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value)
