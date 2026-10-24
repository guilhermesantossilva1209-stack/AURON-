package br.com.auron.automation

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppResolutionTest {
    private val personal = LaunchableApp("com.whatsapp", "WhatsApp")
    private val business = LaunchableApp("com.whatsapp.w4b", "WhatsApp Business")
    private val agenda = LaunchableApp("com.example.agenda", "Agenda diária")

    @Test fun `an exact label wins over partial matches`() {
        assertEquals(AppMatch.Single(personal), AppResolution.match("WhatsApp", listOf(business, personal)))
    }

    @Test fun `an exact package disambiguates identical app labels`() {
        val other = LaunchableApp("com.example.other", "WhatsApp")
        assertEquals(AppMatch.Single(personal), AppResolution.match("com.whatsapp", listOf(personal, other)))
        assertEquals(AppMatch.Ambiguous(listOf(other, personal)), AppResolution.match("whatsapp", listOf(personal, other)))
    }

    @Test fun `ambiguous partial names require selection instead of arbitrary launch`() {
        assertEquals(AppMatch.Ambiguous(listOf(personal, business)), AppResolution.match("Whats", listOf(business, personal)))
    }

    @Test fun `diacritics whitespace and case do not depend on device locale`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(AppMatch.Single(agenda), AppResolution.match("  AGENDA   DIARIA ", listOf(agenda)))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test fun `invalid and absent names never resolve to any app`() {
        listOf("", " ", "X", "http://example.com", "WhatsApp\nabrir outra coisa", "a".repeat(121))
            .forEach { assertEquals(AppMatch.NotFound, AppResolution.match(it, listOf(personal))) }
    }

    @Test fun `multiple launcher activities of one package do not create ambiguity`() {
        assertEquals(AppMatch.Single(personal), AppResolution.match("Whats", listOf(personal, personal)))
    }

    @Test fun `short names are allowed only when exact`() {
        val app = LaunchableApp("com.example.x", "X")
        assertEquals(AppMatch.Single(app), AppResolution.match("x", listOf(app, personal)))
        assertEquals(AppMatch.NotFound, AppResolution.match("w", listOf(app, personal)))
    }
}
