package br.com.auron.automation

import org.junit.Assert.*
import org.junit.Test

class NotificationPolicyTest {
    private val settings = AutomationSettings(enabled = true, allowedPackages = setOf("com.whatsapp"))
    private val notification = AuthorizedNotification("n", 7, "com.whatsapp", "WhatsApp", "Destinatário", "Mensagem", 10, true, true)
    private val draft = ReplyDraft("d", "n", 7, "WhatsApp", "Destinatário", "Resposta", "owner", 500)

    @Test fun `default settings never authorize notification access`() {
        assertFalse(NotificationPolicy.canRead("owner", AutomationSettings(), true, true, "com.whatsapp"))
    }

    @Test fun `access requires profile opt in permission unlocked and explicit app allowlist`() {
        assertTrue(NotificationPolicy.canRead("owner", settings, true, true, "com.whatsapp"))
        assertFalse(NotificationPolicy.canRead(null, settings, true, true, "com.whatsapp"))
        assertFalse(NotificationPolicy.canRead("owner", settings.copy(enabled = false), true, true, "com.whatsapp"))
        assertFalse(NotificationPolicy.canRead("owner", settings, false, true, "com.whatsapp"))
        assertFalse(NotificationPolicy.canRead("owner", settings, true, false, "com.whatsapp"))
        assertFalse(NotificationPolicy.canRead("owner", settings, true, true, "com.example.bank"))
    }

    private fun confirm(
        profile: String? = "owner", current: AuthorizedNotification? = notification,
        foreground: Boolean = true, unlocked: Boolean = true, now: Long = 499,
        sent: Boolean = false, access: Boolean = true, value: AutomationSettings = settings,
    ) = NotificationPolicy.canConfirm(draft, profile, current, value, access, foreground, unlocked, now, sent)

    @Test fun `confirmation succeeds only for current available action`() { assertTrue(confirm()) }
    @Test fun `account switch cannot confirm a reply from another profile`() { assertFalse(confirm(profile = "another")); assertFalse(confirm(profile = null)) }
    @Test fun `removed replaced or updated notifications invalidate drafts`() {
        assertFalse(confirm(current = null))
        assertFalse(confirm(current = notification.copy(id = "other")))
        assertFalse(confirm(current = notification.copy(revision = 8)))
        assertFalse(confirm(current = notification.copy(canReply = false)))
    }
    @Test fun `expired and consumed confirmation cannot send`() { assertFalse(confirm(now = 500)); assertFalse(confirm(sent = true)) }
    @Test fun `background locked revoked or disabled access prevents sending`() {
        assertFalse(confirm(foreground = false)); assertFalse(confirm(unlocked = false)); assertFalse(confirm(access = false))
        assertFalse(confirm(value = settings.copy(enabled = false)))
        assertFalse(confirm(value = settings.copy(allowedPackages = emptySet())))
    }
    @Test fun `valid reply preserves intended accents punctuation and case`() {
        assertEquals("Olá, Ana!\nTudo bem?", NotificationPolicy.replyText("  Olá, Ana!\nTudo bem?  "))
    }
    @Test fun `empty oversized and unsafe replies are rejected`() {
        listOf(" ", "a".repeat(1001), "teste\u0000").forEach {
            assertThrows(IllegalArgumentException::class.java) { NotificationPolicy.replyText(it) }
        }
    }
    @Test fun `whatsapp eligibility only recognizes exact official package names`() {
        assertTrue(NotificationPolicy.isWhatsApp("com.whatsapp"))
        assertTrue(NotificationPolicy.isWhatsApp("com.whatsapp.w4b"))
        assertFalse(NotificationPolicy.isWhatsApp("com.whatsapp.clone"))
        assertFalse(NotificationPolicy.isWhatsApp("com.attacker.whatsapp"))
    }
}
