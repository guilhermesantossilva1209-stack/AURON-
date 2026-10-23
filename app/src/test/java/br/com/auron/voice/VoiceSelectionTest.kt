package br.com.auron.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceSelectionTest {
    private fun voice(name: String, quality: Int = 300, latency: Int = 300) = InstalledVoice(
        name = name, engine = "android.test.engine", language = "pt", country = "BR",
        networkRequired = false, installed = true, quality = quality, latency = latency,
    )

    @Test fun catalogExcludesNetworkMissingLanguageAndUninstalledVoices() {
        val offline = voice("offline")
        val candidates = listOf(offline, voice("network").copy(networkRequired = true), voice("download").copy(installed = false),
            voice("Portugal").copy(country = "PT"), voice("English").copy(language = "en"), voice(""), voice("invalid-engine").copy(engine = ""))
        assertEquals(listOf(offline), VoiceSelection.offlineBrazilian(candidates))
    }

    @Test fun automaticSelectionPrefersQualityThenLatencyAndIsDeterministic() {
        val better = voice("best", quality = 500, latency = 200)
        val candidates = listOf(voice("basic"), voice("slow", quality = 500, latency = 400), better)
        val selection = VoiceSelection.choose(candidates, "", "")
        assertEquals(better, selection.voice)
        assertFalse(selection.usedFallback)
    }

    @Test fun explicitVoiceIsPreservedEvenWhenAnotherHasHigherQuality() {
        val selected = voice("preferred", quality = 100)
        assertEquals(selected, VoiceSelection.choose(listOf(voice("higher", quality = 500), selected), selected.name, selected.engine).voice)
    }

    @Test fun missingVoiceOrChangedEngineFallsBackWithoutSelectingAnOnlineVoice() {
        val fallback = voice("offline")
        val voices = listOf(fallback, voice("saved").copy(networkRequired = true))
        assertEquals(VoiceChoice(fallback, true), VoiceSelection.choose(voices, "saved", fallback.engine))
        assertEquals(VoiceChoice(fallback, true), VoiceSelection.choose(voices, fallback.name, "different.engine"))
    }

    @Test fun noOfflineBrazilianVoiceMeansNoSynthesisCandidate() {
        assertNull(VoiceSelection.choose(listOf(voice("online").copy(networkRequired = true)), "", "").voice)
    }

    @Test fun duplicateAndOversizedProviderVoiceIdsAreDiscarded() {
        val candidate = voice("valid")
        assertEquals(listOf(candidate), VoiceSelection.offlineBrazilian(listOf(candidate, candidate, voice("x".repeat(257)))))
    }
}
