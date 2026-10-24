package br.com.auron.automation

import android.content.Context
import br.com.auron.data.ProfileStorage

/** Private, per-profile metadata. No notification, contact, message, token or draft is written here. */
internal class AutomationPreferences(context: Context, profileId: String) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "auron_automation_${ProfileStorage.digest(profileId)}", Context.MODE_PRIVATE,
    )

    fun read(): AutomationSettings = AutomationSettings(
        enabled = preferences.getBoolean("enabled", false),
        allowedPackages = preferences.getStringSet("allowed_packages", emptySet()).orEmpty().toSet(),
        announceForeground = preferences.getBoolean("announce_foreground", false),
        intervalSeconds = preferences.getInt("interval_seconds", 60).coerceIn(30, 3600),
    )

    fun save(value: AutomationSettings) {
        preferences.edit().putBoolean("enabled", value.enabled)
            .putStringSet("allowed_packages", value.allowedPackages.toSet())
            .putBoolean("announce_foreground", value.announceForeground)
            .putInt("interval_seconds", value.intervalSeconds.coerceIn(30, 3600)).apply()
    }
}
