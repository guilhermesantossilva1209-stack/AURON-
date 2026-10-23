package br.com.auron.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Token storage only. Passwords are never accepted by this store. */
internal class EncryptedSessionStore(context: Context, private val alias: String = "br.com.auron.auth.session.v1") {
    private val file = AtomicFile(File(context.noBackupFilesDir, "auron-auth-session.v1"))
    private val aad = "auron-session-v1".toByteArray(Charsets.UTF_8)

    fun read(projectUrl: String): AuthSession? {
        if (!file.baseFile.exists()) return null
        require(file.baseFile.length() <= 65_536L) { "Invalid encrypted session size" }
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 29 && bytes[0] == 1.toByte()) { "Invalid encrypted session format" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8))
        require(json.getString("project") == projectUrl) { "Session belongs to a different project" }
        return AuthSession(
            userId = json.getString("user_id"), email = json.getString("email"),
            accessToken = json.getString("access_token"), refreshToken = json.getString("refresh_token"),
            expiresAt = json.getLong("expires_at"),
        ).also {
            require(it.userId.isNotBlank() && it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() && it.expiresAt > 0)
        }
    }

    fun save(projectUrl: String, session: AuthSession) {
        val json = JSONObject().put("project", projectUrl).put("user_id", session.userId)
            .put("email", session.email).put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken).put("expires_at", session.expiresAt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        require(cipher.iv.size == 12)
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val bytes = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size).put(1.toByte()).put(cipher.iv).put(encrypted).array()
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    fun clear() {
        // Either removing the ciphertext or destroying its key prevents restoration.
        // Try both even when one storage operation fails (for example a full disk).
        val fileRemoved = runCatching { file.delete(); !file.baseFile.exists() }.getOrDefault(false)
        val keyRemoved = runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry(alias)
            !store.containsAlias(alias)
        }.getOrDefault(false)
        check(fileRemoved || keyRemoved) { "Encrypted session could not be invalidated" }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
}
