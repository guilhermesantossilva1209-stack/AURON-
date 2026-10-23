package br.com.auron.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import br.com.auron.bible.BibleCatalog
import br.com.auron.bible.DailyVerseSelector
import br.com.auron.ui.theme.AuronTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BibleReadingTest {
    @get:Rule val compose = createComposeRule()
    private fun verses() = DailyVerseSelector.select(LocalDate.of(2026, 10, 18),
        BibleCatalog.load(ApplicationProvider.getApplicationContext<Context>()))
    @Test fun openingReadingDoesNotConfirmAndButtonRequiresExplicitClick() {
        var completed = 0
        val daily = verses()
        assertEquals(5, daily.size)
        compose.setContent { AuronTheme { BibleReadingSheet("2026-10-18", daily, false, { completed++ }, {}) } }
        compose.waitForIdle()
        assertEquals(0, completed)
        compose.onNodeWithText("Concluir leitura").performClick()
        assertEquals(1, completed)
    }
    @Test fun reopeningCompletedReadingKeepsItCompleted() {
        val daily = verses()
        compose.setContent { AuronTheme { BibleReadingSheet("2026-10-18", daily, true, { fail("Must not confirm twice") }, {}) } }
        compose.onNodeWithText("Leitura concluída", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Leitura concluída. Você pode reler sempre que quiser.").assertIsDisplayed()
    }
}
