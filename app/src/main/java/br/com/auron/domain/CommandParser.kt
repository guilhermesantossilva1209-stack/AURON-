package br.com.auron.domain

import java.text.Normalizer
import java.util.Locale

sealed interface LocalCommand {
    data object Morning : LocalCommand
    data object Night : LocalCommand
    data object ShowRoutine : LocalCommand
    data object Pending : LocalCommand
    data object Help : LocalCommand
    data class CreateTask(val title: String) : LocalCommand
    data class CompleteTask(val title: String) : LocalCommand
    data class CompleteRoutine(val title: String) : LocalCommand
    data class Navigate(val destination: String) : LocalCommand
    data object Unknown : LocalCommand
}

/**
 * A deliberately small command language, not a language model or tool executor.
 * Parse only direct user input. Documents, transcripts from media and model replies
 * are content and must never be passed here for automatic execution.
 */
object CommandParser {
    private data class Token(val value: String, val start: Int)

    private val tokenPattern = Regex("[^\\s:]+|:")
    private val destinations = mapOf(
        "inicio" to "dashboard",
        "painel" to "dashboard",
        "centro de comando" to "dashboard",
        "chat" to "chat",
        "conversa" to "chat",
        "rotina" to "routine",
        "tarefas" to "tasks",
        "historico" to "history",
        "modulos" to "modules",
        "configuracoes" to "settings",
    )

    fun parse(text: String): LocalCommand {
        if (text.isBlank() || text.length > InputLimits.MESSAGE || text.any(Char::isISOControl)) {
            return LocalCommand.Unknown
        }
        val input = text.trim()
        val normalized = normalize(input).trimEnd('.', '!', '?').trim()
        when {
            Regex("bom dia(?:(?:\\s*,\\s*|\\s+)auron)?").matches(normalized) -> return LocalCommand.Morning
            Regex("boa noite(?:(?:\\s*,\\s*|\\s+)auron)?").matches(normalized) -> return LocalCommand.Night
            normalized in setOf("rotina", "mostre minha rotina", "mostrar rotina", "mostre a rotina") ->
                return LocalCommand.ShowRoutine
            normalized in setOf("o que falta fazer", "o que falta", "pendencias", "tarefas pendentes") ->
                return LocalCommand.Pending
            normalized in setOf("ajuda", "comandos", "o que voce faz", "mostre os comandos") ->
                return LocalCommand.Help
        }

        destinations[normalized]?.let { return LocalCommand.Navigate(it) }
        for (prefix in listOf("abrir ", "abra ", "ir para ", "mostrar ", "mostre ")) {
            if (normalized.startsWith(prefix)) {
                destinations[normalized.removePrefix(prefix)]?.let { return LocalCommand.Navigate(it) }
            }
        }

        val tokens = tokenPattern.findAll(input).map {
            Token(normalize(it.value), it.range.first)
        }.toList()
        if (normalized in setOf(
                "marque a atividade como concluida", "marcar a atividade como concluida",
                "marque atividade como concluida", "marcar atividade como concluida",
                "marque a tarefa como concluida", "marcar a tarefa como concluida",
                "marque tarefa como concluida", "marcar tarefa como concluida",
                "marcar como concluida", "marque como concluida", "concluir",
            )
        ) return LocalCommand.Help

        for (prefix in listOf("criar tarefa", "adicionar tarefa", "nova tarefa")) {
            matchTitle(input, tokens, prefix)?.let { return it.toCommand(LocalCommand::CreateTask) }
        }
        for (prefix in listOf("concluir tarefa", "finalizar tarefa")) {
            matchTitle(input, tokens, prefix)?.let { return it.toCommand(LocalCommand::CompleteTask) }
        }
        for (prefix in listOf("concluir atividade", "finalizar atividade", "concluir item")) {
            matchTitle(input, tokens, prefix)?.let { return it.toCommand(LocalCommand::CompleteRoutine) }
        }
        for (verb in listOf("marcar", "marque")) {
            for (article in listOf("", "a ")) {
                matchMarkedTitle(input, tokens, "$verb ${article}tarefa")?.let {
                    return it.toCommand(LocalCommand::CompleteTask)
                }
                matchMarkedTitle(input, tokens, "$verb ${article}atividade")?.let {
                    return it.toCommand(LocalCommand::CompleteRoutine)
                }
            }
        }
        return LocalCommand.Unknown
    }

    /** Null means no prefix match; an empty title asks for more detail. */
    private fun matchTitle(input: String, tokens: List<Token>, prefix: String): String? {
        val prefixWords = prefix.split(' ')
        if (tokens.take(prefixWords.size).map { it.value } != prefixWords) return null
        var titleIndex = prefixWords.size
        if (tokens.getOrNull(titleIndex)?.value == ":") titleIndex++
        return tokens.getOrNull(titleIndex)?.let { input.substring(it.start).trim() }.orEmpty()
    }

    private fun matchMarkedTitle(input: String, tokens: List<Token>, prefix: String): String? {
        val prefixWords = prefix.split(' ')
        if (tokens.take(prefixWords.size).map { it.value } != prefixWords) return null
        if (tokens.size < prefixWords.size + 2) return ""
        val ending = tokens.takeLast(2).map { it.value.trimEnd('.', '!', '?') }
        if (ending != listOf("como", "concluida")) return null
        var titleIndex = prefixWords.size
        if (tokens.getOrNull(titleIndex)?.value == ":") titleIndex++
        if (titleIndex >= tokens.size - 2) return ""
        return input.substring(tokens[titleIndex].start, tokens[tokens.size - 2].start).trim()
    }

    private fun String.toCommand(factory: (String) -> LocalCommand): LocalCommand = when {
        isBlank() -> LocalCommand.Help
        length > InputLimits.TITLE -> LocalCommand.Unknown
        else -> factory(this)
    }

    internal fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()
}
