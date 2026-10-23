package br.com.auron.data

import java.security.MessageDigest

/** Never use an email, URL or remote user ID directly in file names or Keystore aliases. */
internal object ProfileStorage {
    fun digest(profileId: String): String {
        require(profileId.isNotBlank()) { "O identificador de perfil não pode ficar vazio." }
        return MessageDigest.getInstance("SHA-256")
            .digest(profileId.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    fun databaseName(profileId: String): String =
        if (profileId == "local") "auron.db" else "auron-profile-${digest(profileId)}.db"

    fun settingsName(profileId: String): String =
        if (profileId == "local") "auron_preferences" else "auron_preferences_${digest(profileId)}"
}
