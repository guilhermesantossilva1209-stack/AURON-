package br.com.auron.voice

/** Android's Voice has no reliable gender metadata. Do not infer gender from IDs. */
data class InstalledVoice(
    val name: String,
    val engine: String,
    val language: String,
    val country: String,
    val networkRequired: Boolean,
    val installed: Boolean,
    val quality: Int,
    val latency: Int,
)

data class VoiceChoice(val voice: InstalledVoice?, val usedFallback: Boolean)

object VoiceSelection {
    fun offlineBrazilian(voices: List<InstalledVoice>): List<InstalledVoice> = voices
        .filter {
            it.language.equals("pt", ignoreCase = true) && it.country.equals("BR", ignoreCase = true) &&
                !it.networkRequired && it.installed && it.name.isNotBlank() && it.name.length <= 256 &&
                it.engine.isNotBlank() && it.engine.length <= 256
        }
        .distinctBy { it.engine to it.name }
        .sortedWith(compareByDescending<InstalledVoice> { it.quality }.thenBy { it.latency }.thenBy { it.name })
        .take(64)

    fun choose(voices: List<InstalledVoice>, selectedName: String, selectedEngine: String): VoiceChoice {
        val available = offlineBrazilian(voices)
        val requested = selectedName.isNotBlank() || selectedEngine.isNotBlank()
        val selected = available.firstOrNull { it.name == selectedName && it.engine == selectedEngine }
        return VoiceChoice(selected ?: available.firstOrNull(), usedFallback = requested && selected == null)
    }
}
