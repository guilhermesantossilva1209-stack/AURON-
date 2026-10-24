package br.com.auron.automation

import java.text.Normalizer
import java.util.Locale

sealed interface AutomationCommand {
    data class OpenApp(val query: String) : AutomationCommand
    data object OpenPanel : AutomationCommand
    data object ReadNotifications : AutomationCommand
    data class PrepareWhatsAppReply(val text: String) : AutomationCommand
}

/** Only direct text/voice input may enter here, after the existing internal commands. */
object AutomationCommandParser {
    fun parse(input: String): AutomationCommand? {
        val original = input.trim()
        if (original.isEmpty() || original.length > 1200 || original.any { it.isISOControl() }) return null
        val normalized = normalize(original)
        if (normalized in setOf("automacoes", "abrir automacoes", "mostrar notificacoes", "minhas notificacoes")) {
            return AutomationCommand.OpenPanel
        }
        if (normalized in setOf("ler notificacoes", "leia as notificacoes", "ler minhas notificacoes")) {
            return AutomationCommand.ReadNotifications
        }
        val reply = Regex("^(?:responda|responder) (?:no|ao) whatsapp\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(original)?.groupValues?.get(1)
        if (reply != null) return runCatching { AutomationCommand.PrepareWhatsAppReply(NotificationPolicy.replyText(reply)) }.getOrNull()
        val app = Regex("^(?:abrir|abra) (?:o aplicativo |aplicativo |o app |app )?(.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(original)?.groupValues?.get(1)?.trim() ?: return null
        if (app.length !in 1..AppResolution.MAX_QUERY_LENGTH || app.contains(Regex("[\\n\\r;:]"))) return null
        return AutomationCommand.OpenApp(app)
    }

    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
