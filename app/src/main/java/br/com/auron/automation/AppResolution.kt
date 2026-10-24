package br.com.auron.automation

import java.text.Normalizer
import java.util.Locale

data class LaunchableApp(val packageName: String, val label: String)

sealed interface AppMatch {
    data class Single(val app: LaunchableApp) : AppMatch
    data class Ambiguous(val apps: List<LaunchableApp>) : AppMatch
    data object NotFound : AppMatch
}

/** Resolves a name from a direct user command, never notification or model content. */
object AppResolution {
    const val MAX_QUERY_LENGTH = 120

    fun match(query: String, apps: List<LaunchableApp>): AppMatch {
        if (query.isBlank() || query.length > MAX_QUERY_LENGTH || query.any(Char::isISOControl)) {
            return AppMatch.NotFound
        }
        val term = normalize(query)
        if (term.isBlank()) return AppMatch.NotFound
        val candidates = apps.filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ normalize(it.label) }, { it.packageName }))
        val packageMatch = candidates.filter { normalize(it.packageName) == term }
        if (packageMatch.isNotEmpty()) return result(packageMatch)
        val exact = candidates.filter { normalize(it.label) == term }
        if (exact.isNotEmpty()) return result(exact)
        // A short partial name must not launch an unrelated application by accident.
        if (term.length < 2) return AppMatch.NotFound
        return result(candidates.filter { normalize(it.label).contains(term) })
    }

    private fun result(apps: List<LaunchableApp>): AppMatch = when (apps.size) {
        0 -> AppMatch.NotFound
        1 -> AppMatch.Single(apps.single())
        else -> AppMatch.Ambiguous(apps)
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .trim()
        .replace(Regex("\\s+"), " ")
}
