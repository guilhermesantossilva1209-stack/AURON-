package br.com.auron.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.auron.domain.ChatMessage
import br.com.auron.domain.InputLimits
import br.com.auron.domain.Period
import br.com.auron.domain.Task
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** These tests use an actual file-backed SQLite database, including close/reopen. */
@RunWith(AndroidJUnit4::class)
class RoomRepositoryPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: AuronDatabase
    private lateinit var repository: RoomAuronRepository
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "persistence-${UUID.randomUUID()}.db"
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, AuronDatabase::class.java, databaseName).build()
        repository = RoomAuronRepository(database)
    }

    private fun reopen() {
        database.close()
        openDatabase()
    }

    @Test
    fun taskHistoryChatAndRoutineCompletionSurviveReopen() = runBlocking {
        repository.initialize()
        val original = Task("task-1", "Estudar edição", "Ciclo principal", 2, "2026-10-18", 100L)
        repository.saveTask(original)
        repository.setTaskCompleted(original.id, true, 200L)
        repository.setTaskCompleted(original.id, true, 300L)
        val morning = repository.routineItems.first().first { it.period == Period.MORNING }
        repository.setRoutineCompleted(morning, "2026-10-17", true, 250L)
        val message = ChatMessage("message-1", "user", "Bom dia, AURON", 400L, "voice")
        repository.addMessage(message)

        reopen()
        repository.initialize()

        assertEquals(original.copy(completedAt = 200L), repository.tasks.first().single())
        assertEquals(message, repository.messages.first().single())
        val completion = repository.routineCompletions.first().single()
        assertEquals(morning.title, completion.title)
        assertEquals("2026-10-17", completion.day)
        assertEquals(250L, completion.completedAt)

        repository.setTaskCompleted(original.id, false, 500L)
        assertNull(repository.tasks.first().single().completedAt)
        repository.setTaskCompleted(original.id, true, 600L)
        assertEquals(600L, repository.tasks.first().single().completedAt)
    }

    @Test
    fun initializationIsIdempotentAndDeletedRoutinesDoNotReturn() = runBlocking {
        repository.initialize()
        repository.initialize()
        val initial = repository.routineItems.first()
        assertEquals(11, initial.count { it.period == Period.MORNING })
        assertEquals(4, initial.count { it.period == Period.NIGHT })
        initial.forEach { repository.deleteRoutine(it.id) }

        reopen()
        repository.initialize()

        assertTrue(repository.routineItems.first().isEmpty())
    }

    @Test
    fun routineHistoryRetainsSnapshotsAcrossEditsAndDeletion() = runBlocking {
        repository.initialize()
        val original = repository.routineItems.first().first()
        repository.setRoutineCompleted(original, "2026-10-17", true, 100L)
        val edited = original.copy(title = "Rotina atualizada", period = Period.NIGHT)
        repository.saveRoutine(edited)
        repository.setRoutineCompleted(edited, "2026-10-17", true, 200L)
        repository.setRoutineCompleted(edited, "2026-10-18", true, 300L)
        repository.deleteRoutine(original.id)

        reopen()

        val history = repository.routineCompletions.first().associateBy { it.day }
        assertEquals(2, history.size)
        assertEquals(original.title, history.getValue("2026-10-17").title)
        assertEquals(original.period, history.getValue("2026-10-17").period)
        assertEquals(100L, history.getValue("2026-10-17").completedAt)
        assertEquals(edited.title, history.getValue("2026-10-18").title)
        assertEquals(edited.period, history.getValue("2026-10-18").period)
        assertTrue(repository.routineItems.first().none { it.id == original.id })
    }

    @Test
    fun clearAllDeletesContentAndDoesNotReseedOnNextOpen() = runBlocking {
        repository.initialize()
        repository.saveTask(Task("task-1", "Tarefa privada", createdAt = 100L))
        repository.addMessage(ChatMessage("message-1", "user", "Registro privado", 100L))
        repository.setRoutineCompleted(repository.routineItems.first().first(), "2026-10-17", true, 100L)
        repository.clearAll()

        reopen()
        repository.initialize()

        assertTrue(repository.tasks.first().isEmpty())
        assertTrue(repository.messages.first().isEmpty())
        assertTrue(repository.routineItems.first().isEmpty())
        assertTrue(repository.routineCompletions.first().isEmpty())
    }

    @Test
    fun clearBeforeFirstInitializationStillRespectsTheEmptyState() = runBlocking {
        repository.clearAll()
        reopen()
        repository.initialize()
        assertTrue(repository.routineItems.first().isEmpty())
    }

    @Test
    fun simultaneousTimestampMessagesKeepConversationOrderAfterReopen() = runBlocking {
        val user = ChatMessage("z-user", "user", "Bom dia", 100L)
        val reply = ChatMessage("a-assistant", "assistant", "Rotina aberta", 100L, "demo")
        repository.addMessage(user)
        repository.addMessage(reply)
        reopen()
        assertEquals(listOf(user, reply), repository.messages.first())
    }

    @Test
    fun assistantResponsesHaveTheirOwnLimitAndAreNeverSilentlyTruncated() = runBlocking {
        val response = ChatMessage("long-reply", "assistant", "R".repeat(InputLimits.RESPONSE), 100L, "ai")
        repository.addMessage(response)
        reopen()
        assertEquals(response, repository.messages.first().single())
        for (invalid in listOf(
            ChatMessage("too-long-user", "user", "U".repeat(InputLimits.MESSAGE + 1), 200L),
            response.copy(id = "too-long-reply", text = response.text + "R"),
        )) {
            try {
                repository.addMessage(invalid)
                fail("Uma mensagem acima do limite deveria ser rejeitada.")
            } catch (_: IllegalArgumentException) {
                // Rejection occurs before persistence; the prior message stays intact.
            }
        }
        assertEquals(listOf(response), repository.messages.first())
    }
}
