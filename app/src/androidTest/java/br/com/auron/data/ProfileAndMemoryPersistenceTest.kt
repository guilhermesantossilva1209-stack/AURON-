package br.com.auron.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.auron.domain.ChatMessage
import br.com.auron.domain.MemoryRecord
import br.com.auron.domain.Task
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAndMemoryPersistenceTest {
    private lateinit var context: Context
    private val profiles = mutableListOf<String>()
    private val databases = mutableListOf<AuronDatabase>()

    @Before
    fun setUp() { context = ApplicationProvider.getApplicationContext() }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        profiles.forEach { profile ->
            context.deleteDatabase(AuronDatabase.databaseName(profile))
            keys.deleteEntry("br.com.auron.memory.v1.${ProfileStorage.digest(profile)}")
        }
    }

    private fun newProfile(): String = "test-profile-${UUID.randomUUID()}".also { profiles += it }
    private fun open(profile: String): AuronDatabase = Room.databaseBuilder(
        context, AuronDatabase::class.java, AuronDatabase.databaseName(profile),
    ).addMigrations(AuronDatabase.MIGRATION_1_2).build().also { databases += it }

    @Test
    fun profileFilesAreIsolatedAndMemoryIsEncryptedAndReadableAfterReopen() = runBlocking {
        val a = newProfile()
        val b = newProfile()
        assertNotEquals(AuronDatabase.databaseName(a), AuronDatabase.databaseName(b))
        assertEquals("auron.db", AuronDatabase.databaseName("local"))
        val db = open(a)
        val cipher = AndroidKeystoreMemoryCipher(a)
        var repository = RoomAuronRepository(db, cipher)
        val other = RoomAuronRepository(open(b), AndroidKeystoreMemoryCipher(b))
        val memory = MemoryRecord("memory-1", "Objetivo privado", "Estudar audiovisual por doze semanas", 123L)
        repository.saveTask(Task("task-1", "Tarefa do perfil A", createdAt = 1L))
        repository.saveMemory(memory)

        val encrypted = requireNotNull(db.dao().memory(memory.id))
        assertFalse(encrypted.ciphertext.toString(Charsets.UTF_8).contains(memory.title))
        assertFalse(encrypted.ciphertext.toString(Charsets.UTF_8).contains(memory.content))
        assertEquals(12, encrypted.iv.size)
        repository.saveMemory(memory)
        assertFalse(encrypted.iv.contentEquals(requireNotNull(db.dao().memory(memory.id)).iv))
        assertTrue(other.tasks.first().isEmpty())
        assertTrue(other.memories.first().isEmpty())

        db.close()
        repository = RoomAuronRepository(open(a), AndroidKeystoreMemoryCipher(a))
        assertEquals(memory, repository.memories.first().single())
        repository.deleteMemory(memory.id)
        assertTrue(repository.memories.first().isEmpty())
    }

    @Test
    fun encryptedPayloadCannotBeMovedToADifferentMemoryId() {
        val profile = newProfile()
        val cipher = AndroidKeystoreMemoryCipher(profile)
        val payload = cipher.encrypt("first-id", "segredo")
        try {
            cipher.decrypt("second-id", payload.ciphertext, payload.iv)
            fail("A autenticação GCM deveria rejeitar um identificador diferente.")
        } catch (_: GeneralSecurityException) {
            // Correct record identity is authenticated along with its ciphertext.
        }
    }

    @Test
    fun readingsKeepTheFirstSelectionAndSurviveReopenAndClear() = runBlocking {
        val profile = newProfile()
        val db = open(profile)
        var repository = RoomAuronRepository(db, AndroidKeystoreMemoryCipher(profile))
        val first = repository.getOrCreateReading("2026-10-18", listOf("a", "b", "c", "d", "e"))
        assertEquals(first, repository.getOrCreateReading("2026-10-18", listOf("f", "g", "h", "i", "j")))
        db.close()
        repository = RoomAuronRepository(open(profile), AndroidKeystoreMemoryCipher(profile))
        assertEquals(first, repository.readings.first().single())
        repository.clearAll()
        assertTrue(repository.readings.first().isEmpty())
    }

    @Test
    fun explicitLegacyImportMergesOnceWithoutOverwritingUserDataOrRemovingSource() = runBlocking {
        val sourceProfile = newProfile()
        val targetProfile = newProfile()
        val sourceDb = open(sourceProfile)
        val sourceCipher = AndroidKeystoreMemoryCipher(sourceProfile)
        val source = RoomAuronRepository(sourceDb, sourceCipher)
        val target = RoomAuronRepository(open(targetProfile), AndroidKeystoreMemoryCipher(targetProfile), sourceDb, sourceCipher)
        source.initialize()
        target.initialize()
        source.saveTask(Task("legacy-only", "Minha tarefa antiga", createdAt = 1L))
        source.saveTask(Task("same-task", "Título antigo", createdAt = 2L))
        target.saveTask(Task("same-task", "Título novo da conta", createdAt = 3L))
        source.addMessage(ChatMessage("same-message", "user", "Conversa antiga", 1L))
        target.addMessage(ChatMessage("same-message", "user", "Conversa nova", 2L))
        val sourceItems = source.routineItems.first().associateBy { it.id }
        source.saveRoutine(sourceItems.getValue("initial_morning_0").copy(title = "Seed personalizado no legado"))
        source.saveRoutine(sourceItems.getValue("initial_morning_1").copy(title = "Outra rotina antiga"))
        val targetItem = target.routineItems.first().first { it.id == "initial_morning_1" }
        target.saveRoutine(targetItem.copy(title = "Rotina editada na conta"))
        val usedTarget = target.routineItems.first().first { it.id == "initial_morning_2" }
        target.setRoutineCompleted(usedTarget, "2026-10-18", true, 20L)
        source.saveRoutine(sourceItems.getValue(usedTarget.id).copy(title = "Água no legado"))
        source.setRoutineCompleted(sourceItems.getValue(usedTarget.id), "2026-10-18", true, 10L)
        val oldMemory = MemoryRecord("legacy-memory", "Projeto antigo", "Conteúdo preservado e recifrado", 1L)
        source.saveMemory(oldMemory)
        target.getOrCreateReading("2026-10-18", listOf("account-verse"))
        source.getOrCreateReading("2026-10-18", listOf("legacy-verse"))

        assertEquals(1, target.tasks.first().size) // No implicit import on initialization.
        assertTrue(target.memories.first().isEmpty())
        assertTrue(target.importLegacyData())
        assertFalse(target.importLegacyData())

        assertEquals(2, target.tasks.first().size)
        assertEquals("Título novo da conta", target.tasks.first().first { it.id == "same-task" }.title)
        assertEquals("Conversa nova", target.messages.first().single().text)
        val merged = target.routineItems.first().associateBy { it.id }
        assertEquals(15, merged.size)
        assertEquals("Seed personalizado no legado", merged.getValue("initial_morning_0").title)
        assertEquals("Rotina editada na conta", merged.getValue("initial_morning_1").title)
        assertEquals(usedTarget.title, merged.getValue(usedTarget.id).title)
        assertEquals(20L, target.routineCompletions.first().single().completedAt)
        assertEquals(oldMemory, target.memories.first().single())
        assertEquals(listOf("account-verse"), target.readings.first().single().verseIds)
        assertEquals(2, source.tasks.first().size)
        assertEquals("Conversa antiga", source.messages.first().single().text)
        assertEquals(oldMemory, source.memories.first().single())
        target.clearAll()
        assertTrue(target.memories.first().isEmpty())
        assertTrue(target.readings.first().isEmpty())
        assertFalse(target.importLegacyData())
        assertEquals(2, source.tasks.first().size)
    }

    @Test
    fun failedDecryptionRollsBackTheWholeImportAndAllowsRetry() = runBlocking {
        val sourceProfile = newProfile()
        val targetProfile = newProfile()
        val sourceDb = open(sourceProfile)
        val targetDb = open(targetProfile)
        val sourceCipher = AndroidKeystoreMemoryCipher(sourceProfile)
        val source = RoomAuronRepository(sourceDb, sourceCipher)
        val target = RoomAuronRepository(targetDb, AndroidKeystoreMemoryCipher(targetProfile), sourceDb, sourceCipher)
        source.saveTask(Task("legacy-task", "Não copiar parcialmente", createdAt = 1L))
        source.saveMemory(MemoryRecord("broken", "Teste", "Memória original", 1L))
        val original = requireNotNull(sourceDb.dao().memory("broken"))
        sourceDb.dao().saveMemory(original.copy(ciphertext = byteArrayOf(0, 1, 2)))
        var rejected = false
        try { target.importLegacyData() } catch (_: Exception) { rejected = true }
        assertTrue(rejected)
        assertTrue(target.tasks.first().isEmpty())
        assertNull(targetDb.dao().metadata("legacy_profile_imported_v1"))
        sourceDb.dao().saveMemory(original)
        assertTrue(target.importLegacyData())
        assertEquals("legacy-task", target.tasks.first().single().id)
    }
}
