package br.com.auron.bible

import br.com.auron.domain.Period
import br.com.auron.domain.RoutineItem
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyVerseSelectorTest {
    private val verses = List(35) { BibleVerse("id$it", "ref$it", "text$it", "reflection$it") }
    @Test fun stableAcrossCallsAndDifferentAcrossAdjacentDays() {
        val date = LocalDate.of(2026, 10, 18)
        assertEquals(DailyVerseSelector.select(date, verses), DailyVerseSelector.select(date, verses))
        assertEquals(5, DailyVerseSelector.select(date, verses).size)
        val week = (0L..6L).flatMap { DailyVerseSelector.select(date.plusDays(it), verses) }
        assertEquals(35, week.map { it.id }.distinct().size)
        assertEquals(DailyVerseSelector.select(date, verses), DailyVerseSelector.select(date.plusDays(7), verses))
    }
    @Test fun handlesDatesBeforeEpochAndLeapDay() {
        for (date in listOf(LocalDate.of(1900, 1, 1), LocalDate.of(2028, 2, 29))) {
            assertEquals(5, DailyVerseSelector.select(date, verses).size)
        }
    }
    @Test fun recognizesOnlyExplicitActionOrExactLegacyMorningTitles() {
        assertTrue(RoutineItem("b", "Ler cinco versículos bíblicos", Period.MORNING, 0).isBibleReading())
        assertTrue(RoutineItem("b", "Minha leitura", Period.MORNING, 0, "bible_reading").isBibleReading())
        assertFalse(RoutineItem("b", "Comprar livro de versículos bíblicos", Period.MORNING, 0).isBibleReading())
        assertFalse(RoutineItem("b", "Ler cinco versículos bíblicos", Period.NIGHT, 0).isBibleReading())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateReferences() {
        DailyVerseSelector.select(LocalDate.now(), List(5) { verses.first() })
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsIncompleteGroup() {
        DailyVerseSelector.select(LocalDate.now(), verses.take(6))
    }

    @Test fun doesNotRepeatAnyVerseInThePreviousSixDaysAcrossTenYears() {
        val start = LocalDate.of(2024, 1, 1)
        val recent = ArrayDeque<Set<String>>()
        repeat(3660) { offset ->
            val selected = DailyVerseSelector.select(start.plusDays(offset.toLong()), verses).map { it.id }.toSet()
            assertEquals(5, selected.size)
            assertTrue(recent.none { earlier -> earlier.any { it in selected } })
            recent.addLast(selected)
            if (recent.size > 6) recent.removeFirst()
        }
    }

    @Test fun leapDayAndNewYearEachAdvanceToANewGroup() {
        for (before in listOf(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29), LocalDate.of(2026, 12, 31))) {
            val first = DailyVerseSelector.select(before, verses).map { it.id }.toSet()
            val next = DailyVerseSelector.select(before.plusDays(1), verses).map { it.id }.toSet()
            assertTrue(first.intersect(next).isEmpty())
        }
    }

    @Test fun localMidnightChangesTheSelectionWhileUtcMidnightDoesNotChangeBrazilianDay() {
        val brazil = ZoneId.of("America/Sao_Paulo")
        fun selection(instant: String) = DailyVerseSelector.select(Instant.parse(instant).atZone(brazil).toLocalDate(), verses)
        assertEquals(selection("2026-10-19T00:00:00Z"), selection("2026-10-19T02:59:59Z"))
        assertNotEquals(selection("2026-10-19T02:59:59Z"), selection("2026-10-19T03:00:00Z"))
        assertEquals(selection("2026-10-19T03:00:00Z"), selection("2026-10-20T02:59:59Z"))
    }

    @Test fun deviceTimezoneDeterminesItsCalendarDay() {
        val instant = Instant.parse("2026-10-19T02:00:00Z")
        val brazilDay = instant.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate()
        val tokyoDay = instant.atZone(ZoneId.of("Asia/Tokyo")).toLocalDate()
        assertEquals(LocalDate.of(2026, 10, 18), brazilDay)
        assertEquals(LocalDate.of(2026, 10, 19), tokyoDay)
        assertNotEquals(DailyVerseSelector.select(brazilDay, verses), DailyVerseSelector.select(tokyoDay, verses))
    }

    @Test fun restoresPreviouslyPresentedIdsEvenIfCatalogOrderChanges() {
        val day = LocalDate.of(2026, 10, 19)
        val original = DailyVerseSelector.select(day, verses)
        assertEquals(original, DailyVerseSelector.restore(original.map { it.id }, verses.reversed()))
    }

    @Test(expected = IllegalArgumentException::class) fun refusesDuplicateSavedIds() {
        DailyVerseSelector.restore(List(5) { "id0" }, verses)
    }

    @Test(expected = IllegalArgumentException::class) fun refusesUnknownSavedId() {
        DailyVerseSelector.restore(listOf("id0", "id1", "id2", "id3", "removed-id"), verses)
    }

    @Test(expected = IllegalArgumentException::class) fun refusesIncompleteSavedReading() {
        DailyVerseSelector.restore(listOf("id0", "id1"), verses)
    }

    @Test fun explicitActionsAndCustomizedRoutineLabelsStayPreserved() {
        assertTrue(RoutineItem("b", "Minha leitura personalizada", Period.NIGHT, 0, "bible_reading").isBibleReading())
        assertTrue(RoutineItem("b", "  Ler  os 5 VERSÍCULOS bíblicos. ", Period.MORNING, 0).isBibleReading())
        assertFalse(RoutineItem("b", "Ler cinco versículos bíblicos", Period.MORNING, 0, "other_action").isBibleReading())
        assertFalse(RoutineItem("b", "Ler cinco versículos bíblicos e anotar", Period.MORNING, 0).isBibleReading())
    }
}
