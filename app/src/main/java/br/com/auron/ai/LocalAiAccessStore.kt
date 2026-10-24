package br.com.auron.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import br.com.auron.auth.AuthPolicies
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Runtime gateway credential only; separate from Supabase sessions and excluded from backups.
 * Changing the gateway origin cannot silently forward its old credential to a different server.
 */
class LocalAiAccessStore(context: Context, private val alias: String = "br.com.auron.ai.local-access.v1") {
    private val file = AtomicFile(File(context.noBackupFilesDir, "auron-ai-local-access.v1"))
    private val aad = "auron-ai-local-access-v1".toByteArray(Charsets.UTF_8)

    fun read(baseUrl: String): String? {
        if (baseUrl.isBlank() || !file.baseFile.exists()) return null
        val origin = origin(baseUrl)
        require(file.baseFile.length() <= 8_192L) { "Invalid encrypted gateway credential size" }
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 29 && bytes[0] == 1.toByte()) { "Invalid encrypted gateway credential format" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8))
        if (json.getString("origin") != origin) return null
        return json.getString("token").also(AiAccess::requireLocalToken)
    }

    fun save(baseUrl: String, token: String) {
        AiAccess.requireLocalToken(token)
        val json = JSONObject().put("origin", origin(baseUrl)).put("token", token)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        require(cipher.iv.size == 12)
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val bytes = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size).put(1.toByte()).put(cipher.iv).put(encrypted).array()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }

    /** Only the gateway credential is invalidated; sessions, routines and memories are untouched. */
    fun clear() {
        val fileRemoved = runCatching { file.delete(); !file.baseFile.exists() }.getOrDefault(false)
        val keyRemoved = runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry(alias)
            !store.containsAlias(alias)
        }.getOrDefault(false)
        check(fileRemoved || keyRemoved) { "Encrypted gateway credential could not be invalidated" }
    }

    private fun origin(baseUrl: String): String {
        val value = baseUrl.trim().trimEnd('/')
        AuthPolicies.requireHttps(value)
        require(URI(value).path.orEmpty().isEmpty()) { "Use the gateway root URL" }
        return value
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
