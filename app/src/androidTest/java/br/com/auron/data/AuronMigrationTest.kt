package br.com.auron.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.auron.domain.ChatMessage
import br.com.auron.domain.Period
import br.com.auron.domain.RoutineItem
import br.com.auron.domain.Task
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opens the real v1 SQL layout through Room's v1 -> v2 migration and schema validation. */
@RunWith(AndroidJUnit4::class)
class AuronMigrationTest {
    private lateinit var context: Context
    private lateinit var name: String
    private var database: AuronDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        name = "migration-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            // Literal schema of the unmodified v0.1 entities, including all original indexes.
            old.execSQL("CREATE TABLE tasks (id TEXT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, priority INTEGER NOT NULL, dueDate TEXT, createdAt INTEGER NOT NULL, completedAt INTEGER, PRIMARY KEY(id))")
            old.execSQL("CREATE INDEX index_tasks_completedAt ON tasks (completedAt)")
            old.execSQL("CREATE TABLE routine_items (id TEXT NOT NULL, title TEXT NOT NULL, period TEXT NOT NULL, orderIndex INTEGER NOT NULL, PRIMARY KEY(id))")
            old.execSQL("CREATE INDEX index_routine_items_period_orderIndex ON routine_items (period, orderIndex)")
            old.execSQL("CREATE TABLE routine_completions (itemId TEXT NOT NULL, day TEXT NOT NULL, title TEXT NOT NULL, period TEXT NOT NULL, completedAt INTEGER NOT NULL, PRIMARY KEY(itemId, day))")
            old.execSQL("CREATE INDEX index_routine_completions_day ON routine_completions (day)")
            old.execSQL("CREATE TABLE chat_messages (id TEXT NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, createdAt INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(id))")
            old.execSQL("CREATE INDEX index_chat_messages_createdAt ON chat_messages (createdAt)")
            old.execSQL("CREATE TABLE database_metadata (`key` TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(`key`))")
            old.execSQL("INSERT INTO database_metadata (`key`, value) VALUES ('routines_seeded_v1', '1')")
            old.version = 1
        }
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(name)
    }

    private fun migratedRepository(): RoomAuronRepository {
        val migrated = Room.databaseBuilder(context, AuronDatabase::class.java, name)
            .addMigrations(AuronDatabase.MIGRATION_1_2).build()
        database = migrated
        return RoomAuronRepository(migrated)
    }

    @Test
    fun upgradingPreservesTasksCustomizedRoutineHistoryAndConversation() = runBlocking {
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("INSERT INTO tasks VALUES ('task-old', 'Estudar vídeo', 'Minha observação', 2, '2026-10-20', 100, 200)")
            old.execSQL("INSERT INTO routine_items VALUES ('initial_morning_2', 'Água personalizada', 'MORNING', 2)")
            old.execSQL("INSERT INTO routine_completions VALUES ('initial_morning_2', '2026-10-17', 'Título anterior', 'MORNING', 150)")
            old.execSQL("INSERT INTO chat_messages VALUES ('old-chat', 'user', 'Minha conversa preservada', 100, 'text')")
        }
        val repository = migratedRepository()
        repository.initialize()

        assertEquals(Task("task-old", "Estudar vídeo", "Minha observação", 2, "2026-10-20", 100L, 200L), repository.tasks.first().single())
        assertEquals(RoutineItem("initial_morning_2", "Água personalizada", Period.MORNING, 2, null), repository.routineItems.first().single())
        assertEquals("Título anterior", repository.routineCompletions.first().single().title)
        assertEquals(ChatMessage("old-chat", "user", "Minha conversa preservada", 100L), repository.messages.first().single())
        assertTrue(repository.memories.first().isEmpty())
        assertTrue(repository.readings.first().isEmpty())
        assertEquals(2, database!!.openHelper.readableDatabase.version)
        assertEquals("1", database!!.dao().metadata("routines_seeded_v1"))
    }

    @Test
    fun anIntentionallyEmptyV1RoutineIsNotReseededDuringUpgrade() = runBlocking {
        val repository = migratedRepository()
        repository.initialize()
        assertTrue(repository.routineItems.first().isEmpty())
        repository.getOrCreateReading("2026-10-18", listOf("verse-1", "verse-2"))
        database!!.close()
        val reopened = migratedRepository()
        reopened.initialize()
        assertTrue(reopened.routineItems.first().isEmpty())
        assertEquals(listOf("verse-1", "verse-2"), reopened.readings.first().single().verseIds)
    }
}
