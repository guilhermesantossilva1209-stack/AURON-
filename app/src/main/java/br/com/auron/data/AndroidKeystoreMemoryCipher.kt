package br.com.auron.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedMemoryPayload(val ciphertext: ByteArray, val iv: ByteArray)

interface MemoryCipher {
    fun encrypt(recordId: String, plaintext: String): EncryptedMemoryPayload
    fun decrypt(recordId: String, ciphertext: ByteArray, iv: ByteArray): String
}

/**
 * AES/GCM authenticated encryption, with a separate non-exportable key per profile.
 * Does not claim hardware backing or require biometrics. Key loss is reported as an
 * error; it never silently replaces encrypted records with empty memory.
 */
class AndroidKeystoreMemoryCipher(profileId: String = "local") : MemoryCipher {
    private val scope = ProfileStorage.digest(profileId)
    private val alias = "br.com.auron.memory.v1.$scope"

    override fun encrypt(recordId: String, plaintext: String): EncryptedMemoryPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        cipher.updateAAD(associatedData(recordId))
        return EncryptedMemoryPayload(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)), cipher.iv)
    }

    override fun decrypt(recordId: String, ciphertext: ByteArray, iv: ByteArray): String {
        require(iv.size == 12) { "O registro de memória possui um vetor de autenticação inválido." }
        val key = synchronized(keyLock) {
            keyStore().getKey(alias, null) as? SecretKey
                ?: error("A chave desta memória não está disponível neste dispositivo.")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData(recordId))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun encryptionKey(): SecretKey = synchronized(keyLock) {
        (keyStore().getKey(alias, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    private fun associatedData(recordId: String) = "auron-memory-v1:$scope:$recordId".toByteArray(Charsets.UTF_8)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val keyLock = Any()
    }
}
