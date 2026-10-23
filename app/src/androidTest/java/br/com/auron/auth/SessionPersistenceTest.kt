package br.com.auron.auth

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

/** Dedicated folder AND Keystore alias; never touches a real account session. */
@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var alias: String
    private val session = AuthSession("user-fixture", "test@example.com", "access-token-test-only", "refresh-token-test-only", 2_000_000_000L)
    @Before fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        alias = "auron.session.test.${UUID.randomUUID()}"
        directory = File(app.cacheDir, alias).apply { mkdirs() }
        context = object : ContextWrapper(app) { override fun getNoBackupFilesDir(): File = directory }
    }
    @After fun clean() {
        EncryptedSessionStore(context, alias).clear()
        directory.deleteRecursively()
    }
    @Test fun persistsAcrossStoreInstancesWithoutPlaintextTokens() {
        EncryptedSessionStore(context, alias).save("https://fixture.supabase.co", session)
        val bytes = File(directory, "auron-auth-session.v1").readBytes()
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(session.accessToken))
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(session.email))
        assertEquals(session, EncryptedSessionStore(context, alias).read("https://fixture.supabase.co"))
    }
    @Test fun logoutInvalidationCannotRestoreSavedSession() {
        val store = EncryptedSessionStore(context, alias)
        store.save("https://fixture.supabase.co", session)
        store.clear()
        assertNull(EncryptedSessionStore(context, alias).read("https://fixture.supabase.co"))
    }
    @Test fun refusesDifferentProjectAndTamperedCiphertext() {
        val store = EncryptedSessionStore(context, alias)
        store.save("https://fixture.supabase.co", session)
        assertThrows(IllegalArgumentException::class.java) { store.read("https://other.supabase.co") }
        val file = File(directory, "auron-auth-session.v1")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertThrows(Exception::class.java) { store.read("https://fixture.supabase.co") }
    }
}
