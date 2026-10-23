package br.com.auron.bible

import android.content.Context
import org.json.JSONObject

object BibleCatalog {
    const val CREDIT = "Bíblia Livre (BLIVRE), edição fevereiro de 2018. © Diego Santos, Mario Sérgio e Marco Teles. CC BY 4.0."
    const val SOURCE = "https://ebible.org/porbr2018/copyright.htm"
    const val LICENSE = "https://creativecommons.org/licenses/by/4.0/"

    fun load(context: Context): List<BibleVerse> {
        val data = context.assets.open("bible_daily.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONObject(data).getJSONArray("verses")
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            BibleVerse(item.getString("id"), item.getString("reference"), item.getString("text"), item.getString("reflection"))
        }.also { verses ->
            require(verses.all { it.id.isNotBlank() && it.text.isNotBlank() && it.reference.isNotBlank() && it.reflection.isNotBlank() })
            DailyVerseSelector.select(java.time.LocalDate.of(2026, 1, 1), verses)
        }
    }
}
