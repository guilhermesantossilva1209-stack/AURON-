package br.com.auron.ai

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated directory and Keystore alias: no production credential is read or removed. */
@RunWith(AndroidJUnit4::class)
class LocalAiAccessStoreTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var alias: String
    private val token = "auron_local_" + "a".repeat(43)
    private val origin = "https://gateway.example"

    @Before fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        alias = "auron.local-ai.test.${UUID.randomUUID()}"
        directory = File(app.cacheDir, alias).apply { mkdirs() }
        context = object : ContextWrapper(app) { override fun getNoBackupFilesDir(): File = directory }
    }

    @After fun clean() {
        LocalAiAccessStore(context, alias).clear()
        directory.deleteRecursively()
    }

    @Test fun restartRestoresEncryptedTokenOnlyForItsGateway() {
        LocalAiAccessStore(context, alias).save(origin, token)
        val bytes = File(directory, "auron-ai-local-access.v1").readBytes()
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(token))
        assertEquals(token, LocalAiAccessStore(context, alias).read(origin))
        assertNull(LocalAiAccessStore(context, alias).read("https://different.example"))
    }

    @Test fun tamperingAndInsecureOriginsCannotRestoreCredentials() {
        val store = LocalAiAccessStore(context, alias)
        assertThrows(IllegalArgumentException::class.java) { store.save("http://gateway.example", token) }
        store.save(origin, token)
        val file = File(directory, "auron-ai-local-access.v1")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertThrows(Exception::class.java) { store.read(origin) }
    }

    @Test fun removeCredentialDoesNotTouchOtherPrivateFiles() {
        val unrelated = File(directory, "unrelated-fixture").apply { writeText("keep") }
        val store = LocalAiAccessStore(context, alias)
        store.save(origin, token)
        store.clear()
        assertNull(LocalAiAccessStore(context, alias).read(origin))
        assertEquals("keep", unrelated.readText())
    }
}
