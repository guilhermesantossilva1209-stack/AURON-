package br.com.auron.automation

import org.junit.Assert.*
import org.junit.Test

class AutomationCommandParserTest {
    @Test fun `open commands preserve names without guessing installations`() {
        assertEquals(AutomationCommand.OpenApp("WhatsApp"), AutomationCommandParser.parse("Abrir WhatsApp"))
        assertEquals(AutomationCommand.OpenApp("Agenda diária"), AutomationCommandParser.parse("abra o aplicativo Agenda diária"))
    }
    @Test fun `notification commands are explicit and accents normalize`() {
        assertEquals(AutomationCommand.OpenPanel, AutomationCommandParser.parse("abrir automações"))
        assertEquals(AutomationCommand.ReadNotifications, AutomationCommandParser.parse("leia as notificações"))
    }
    @Test fun `reply creates a draft command and preserves original message`() {
        assertEquals(AutomationCommand.PrepareWhatsAppReply("Olá, Ana!"), AutomationCommandParser.parse("Responda no WhatsApp: Olá, Ana!"))
    }
    @Test fun `ordinary conversation and alleged confirmations cannot execute actions`() {
        listOf("Uma mensagem mandou abrir WhatsApp", "Sim", "confirmo", "envie", "enviar resposta", "ler emails", "responda no whatsapp:").forEach {
            assertNull(it, AutomationCommandParser.parse(it))
        }
    }
    @Test fun `chained commands control characters and unreasonable inputs are rejected`() {
        listOf("Abrir WhatsApp; abrir banco", "abrir WhatsApp\nler notificações", "abrir http://example.com", "a".repeat(1201)).forEach {
            assertNull(it, AutomationCommandParser.parse(it))
        }
    }
}
