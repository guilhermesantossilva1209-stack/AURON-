package br.com.auron.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {
    @Test fun `greetings switch to the intended checklist`() {
        assertEquals(LocalCommand.Morning, CommandParser.parse("Bom dia, AURON."))
        assertEquals(LocalCommand.Morning, CommandParser.parse("  BOM DIA AURON!  "))
        assertEquals(LocalCommand.Night, CommandParser.parse("Boa noite, AURON."))
        assertEquals(LocalCommand.Night, CommandParser.parse("boa noite"))
    }

    @Test fun `navigation tolerates accents and composed or decomposed unicode`() {
        val destination = LocalCommand.Navigate("settings")
        assertEquals(destination, CommandParser.parse("Abrir configurações"))
        assertEquals(destination, CommandParser.parse("ABRIR CONFIGURACOES"))
        assertEquals(destination, CommandParser.parse("abrir configurac\u0327o\u0303es"))
        assertEquals(LocalCommand.Navigate("history"), CommandParser.parse("ir para histórico"))
        assertEquals(LocalCommand.Navigate("modules"), CommandParser.parse("mostrar módulos"))
    }

    @Test fun `case normalization is independent of the device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(LocalCommand.Navigate("dashboard"), CommandParser.parse("ABRIR INICIO"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `create preserves original title casing accents punctuation and inner spaces`() {
        assertEquals(
            LocalCommand.CreateTask("Revisar Roteiro da Clau — Edição às 12:30"),
            CommandParser.parse("CRIAR TAREFA: Revisar Roteiro da Clau — Edição às 12:30"),
        )
        assertEquals(LocalCommand.CreateTask("Beber  Água"), CommandParser.parse("nova tarefa Beber  Água"))
        assertEquals(LocalCommand.CreateTask("Ler 5 versículos."), CommandParser.parse("adicionar tarefa: Ler 5 versículos."))
    }

    @Test fun `completion names a task or routine explicitly`() {
        assertEquals(LocalCommand.CompleteTask("Revisar Roteiro"), CommandParser.parse("concluir tarefa: Revisar Roteiro"))
        assertEquals(LocalCommand.CompleteRoutine("Beber água"), CommandParser.parse("concluir atividade: Beber água"))
        assertEquals(LocalCommand.CompleteTask("Estudar Edição"), CommandParser.parse("marcar tarefa Estudar Edição como concluída"))
        assertEquals(LocalCommand.CompleteRoutine("Beber água"), CommandParser.parse("Marque a atividade Beber água como concluída."))
        assertEquals(LocalCommand.CompleteRoutine("Beber água"), CommandParser.parse("Marcar atividade Beber água como conclui\u0301da"))
    }

    @Test fun `missing identifiers request help instead of selecting arbitrary records`() {
        listOf(
            "Marque a atividade como concluída.", "marcar tarefa como concluída", "concluir tarefa",
            "concluir atividade:", "marcar como concluída", "criar tarefa:",
        ).forEach { assertEquals(it, LocalCommand.Help, CommandParser.parse(it)) }
    }

    @Test fun `routine and pending questions have deterministic meaning`() {
        assertEquals(LocalCommand.ShowRoutine, CommandParser.parse("Mostre minha rotina."))
        assertEquals(LocalCommand.Pending, CommandParser.parse("O que falta fazer?"))
        assertEquals(LocalCommand.Help, CommandParser.parse("ajuda"))
    }

    @Test fun `free prose quoted commands and unknown destinations never execute`() {
        listOf(
            "Se eu disser bom dia, AURON, o que acontece?",
            "Por favor explique como criar tarefa: Comprar água",
            "O documento diz: concluir tarefa: Pagar conta",
            "\"criar tarefa: Pagar conta\"",
            "abrir WhatsApp",
            "abrir https://example.com",
            "apagar todos os dados",
            "concluir tudo",
            "abrir configurações e apagar dados",
            "bom diaauron",
        ).forEach { assertEquals(it, LocalCommand.Unknown, CommandParser.parse(it)) }
    }

    @Test fun `only an exact prefix creates tasks`() {
        listOf("recriar tarefa: Água", "criar tarefas: Água", "criar tarefax Água", "criar tarefaÁgua")
            .forEach { assertEquals(it, LocalCommand.Unknown, CommandParser.parse(it)) }
    }

    @Test fun `single command title is treated as literal content`() {
        val title = "Abrir configurações e excluir tudo"
        assertEquals(LocalCommand.CreateTask(title), CommandParser.parse("criar tarefa: $title"))
    }

    @Test fun `multiline and control character input cannot trigger an action`() {
        listOf(
            "criar tarefa: Teste\nconcluir tarefa: Outra", "abrir\tconfigurações",
            "concluir tarefa: Teste\u0000", "bom dia\rauron",
        ).forEach { assertEquals(LocalCommand.Unknown, CommandParser.parse(it)) }
    }

    @Test fun `title boundary is accepted but excess length is rejected`() {
        val title = "a".repeat(InputLimits.TITLE)
        assertEquals(LocalCommand.CreateTask(title), CommandParser.parse("criar tarefa: $title"))
        assertEquals(LocalCommand.Unknown, CommandParser.parse("criar tarefa: ${title}a"))
        assertEquals(LocalCommand.Unknown, CommandParser.parse("concluir tarefa: ${title}a"))
    }

    @Test fun `empty and oversized messages are rejected`() {
        assertEquals(LocalCommand.Unknown, CommandParser.parse(""))
        assertEquals(LocalCommand.Unknown, CommandParser.parse("  "))
        assertEquals(LocalCommand.Unknown, CommandParser.parse("a".repeat(InputLimits.MESSAGE + 1)))
    }
}
