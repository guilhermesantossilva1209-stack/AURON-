package br.com.auron.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAiProviderTest {
    private val provider = DemoAiProvider()

    private fun reply(input: String, settings: AppSettings = AppSettings()): String = runBlocking {
        val result = provider.reply(input, settings)
        assertTrue(result is ProviderResult.Success)
        (result as ProviderResult.Success).text
    }

    @Test fun `provider explicitly identifies deterministic local demonstration`() {
        assertTrue(provider.isDemo)
        assertEquals("local-demo-v1", provider.id)
        val result = reply("Olá")
        assertTrue(result.startsWith("Demonstração local •"))
        assertTrue(result.contains("Ainda não há um modelo de IA conectado"))
        assertTrue(result.contains("respostas fixas"))
    }

    @Test fun `help requires exact titles and explains supported commands`() {
        val result = reply("Marque a atividade como concluída.")
        assertTrue(result.contains("informe o título exato"))
        assertTrue(result.contains("criar tarefa:"))
        assertFalse(result.contains("foi concluída"))
    }

    @Test fun `sensitive health and finance responses do not add casual humor`() {
        val settings = AppSettings(userName = "CHEFE-TESTE", humor = 100, sarcasm = 100)
        val health = reply("Posso dobrar a dose da medicação?", settings)
        assertTrue(health.contains("Não avalio sintomas nem ajusto medicação"))
        assertFalse(health.contains("CHEFE-TESTE"))
        val finance = reply("Como garantir lucro com a banca?", settings)
        assertTrue(finance.contains("nem prometo retornos"))
        assertFalse(finance.contains("CHEFE-TESTE"))
    }

    @Test fun `emotional support explains the limitation and crisis response gives local help`() {
        assertTrue(reply("Estou ansioso").contains("não substitui acompanhamento profissional"))
        val crisis = reply("Penso em suicídio")
        assertTrue(crisis.contains("192"))
        assertTrue(crisis.contains("188"))
        assertTrue(crisis.contains("não oferece acompanhamento clínico"))
        assertFalse(reply("Quero matar tempo").contains("192"))
    }

    @Test fun `low humor removes the casual salutation`() {
        val result = reply("Olá", AppSettings(userName = "Maria", humor = 0))
        assertFalse(result.contains("Maria, central"))
    }

    @Test fun `oversized or blank prompts return an explicit unavailable result`() = runBlocking {
        assertTrue(provider.reply(" ", AppSettings()) is ProviderResult.Unavailable)
        assertTrue(provider.reply("a".repeat(InputLimits.MESSAGE + 1), AppSettings()) is ProviderResult.Unavailable)
        assertTrue(provider.reply("a".repeat(InputLimits.MESSAGE), AppSettings()) is ProviderResult.Success)
    }

    @Test fun `settings defaults preserve consent and intended personality`() {
        val settings = AppSettings()
        assertFalse(settings.allowOnlineSpeech)
        assertFalse(settings.speakReplies)
        assertEquals(95, settings.humor)
        assertEquals(60, settings.sarcasm)
        assertEquals(55, settings.formality)
        assertEquals(90, settings.proactivity)
    }
}
