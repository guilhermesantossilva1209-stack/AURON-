package br.com.auron.ui

import android.content.Context

/** Only visual flags: available before authentication without opening a private profile. */
internal class OpeningPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("auron.opening.v1", Context.MODE_PRIVATE)
    data class Appearance(val showIntro: Boolean = true, val reducedMotion: Boolean = false)
    fun read() = Appearance(store.getBoolean("show_intro", true), store.getBoolean("reduced_motion", false))
    fun save(showIntro: Boolean, reducedMotion: Boolean) {
        store.edit().putBoolean("show_intro", showIntro).putBoolean("reduced_motion", reducedMotion).apply()
    }
}
