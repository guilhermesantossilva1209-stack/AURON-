package br.com.auron.bible

import br.com.auron.domain.Period
import br.com.auron.domain.RoutineItem
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

data class BibleVerse(val id: String, val reference: String, val text: String, val reflection: String)

/** Fixed groups of five. With the bundled 35 verses, no verse repeats for six days. */
object DailyVerseSelector {
    fun select(day: LocalDate, verses: List<BibleVerse>): List<BibleVerse> {
        require(verses.size >= 5 && verses.size % 5 == 0) { "O catálogo deve conter conjuntos completos de cinco versículos." }
        require(verses.map { it.id }.distinct().size == verses.size) { "Há referências duplicadas no catálogo." }
        val group = Math.floorMod(day.toEpochDay(), (verses.size / 5).toLong()).toInt()
        return verses.subList(group * 5, group * 5 + 5).toList()
    }

    /**
     * An already presented day keeps its saved order, including after catalog updates.
     * Reject damaged/missing references instead of replacing history or rendering
     * duplicate Compose keys. The caller reports the problem without changing rows.
     */
    fun restore(verseIds: List<String>, verses: List<BibleVerse>): List<BibleVerse> {
        require(verseIds.size == 5 && verseIds.distinct().size == 5) {
            "A leitura registrada precisa conter cinco versículos diferentes. O histórico foi preservado."
        }
        val byId = verses.associateBy { it.id }
        require(byId.size == verses.size) { "Há referências duplicadas no catálogo." }
        return verseIds.map { id ->
            requireNotNull(byId[id]) {
                "O catálogo não contém uma leitura já registrada. Nenhum histórico foi alterado."
            }
        }
    }
}

/** Exact legacy labels only; customized activities never acquire actions by vague matching. */
fun RoutineItem.isBibleReading(): Boolean {
    if (action == "bible_reading") return true
    if (action != null || period != Period.MORNING) return false
    val label = Normalizer.normalize(title.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trimEnd('.')
    return label in setOf("ler cinco versiculos biblicos", "ler os 5 versiculos biblicos", "ler 5 versiculos biblicos")
}
