package br.com.auron.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AuronDao {
    @Query("SELECT * FROM tasks ORDER BY (completedAt IS NOT NULL), priority DESC, createdAt DESC, id")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM routine_items ORDER BY period, orderIndex, id")
    fun observeRoutineItems(): Flow<List<RoutineItemEntity>>

    @Query("SELECT * FROM routine_completions ORDER BY day DESC, completedAt DESC, itemId")
    fun observeRoutineCompletions(): Flow<List<RoutineCompletionEntity>>

    // rowid preserves insertion order when a local reply shares the user's millisecond.
    @Query("SELECT * FROM chat_messages ORDER BY createdAt, rowid")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC, id")
    fun observeMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM daily_readings ORDER BY day DESC")
    fun observeReadings(): Flow<List<DailyReadingEntity>>

    @Query("SELECT * FROM daily_readings WHERE day = :day")
    suspend fun reading(day: String): DailyReadingEntity?

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun memory(id: String): MemoryEntity?

    @Query("SELECT COUNT(*) FROM routine_completions WHERE itemId = :id")
    suspend fun completionCount(id: String): Int

    @Query("SELECT * FROM tasks")
    suspend fun allTasks(): List<TaskEntity>

    @Query("SELECT * FROM routine_items")
    suspend fun allRoutines(): List<RoutineItemEntity>

    @Query("SELECT * FROM routine_completions")
    suspend fun allCompletions(): List<RoutineCompletionEntity>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt, rowid")
    suspend fun allMessages(): List<ChatMessageEntity>

    @Query("SELECT * FROM memories")
    suspend fun allMemories(): List<MemoryEntity>

    @Query("SELECT * FROM daily_readings")
    suspend fun allReadings(): List<DailyReadingEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: String): TaskEntity?

    @Query("SELECT * FROM routine_items WHERE id = :id")
    suspend fun routine(id: String): RoutineItemEntity?

    @Query("SELECT value FROM database_metadata WHERE `key` = :key")
    suspend fun metadata(key: String): String?

    @Upsert
    suspend fun saveTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Upsert
    suspend fun saveRoutine(item: RoutineItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoutines(items: List<RoutineItemEntity>)

    @Query("DELETE FROM routine_items WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: RoutineCompletionEntity)

    @Query("DELETE FROM routine_completions WHERE itemId = :itemId AND day = :day")
    suspend fun deleteCompletion(itemId: String, day: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addMessage(message: ChatMessageEntity)

    @Upsert
    suspend fun saveMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReading(reading: DailyReadingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importMessages(messages: List<ChatMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importCompletions(completions: List<RoutineCompletionEntity>)

    @Upsert
    suspend fun saveMetadata(metadata: MetadataEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM routine_items")
    suspend fun clearRoutineItems()

    @Query("DELETE FROM routine_completions")
    suspend fun clearRoutineCompletions()

    @Query("DELETE FROM memories")
    suspend fun clearMemories()

    @Query("DELETE FROM daily_readings")
    suspend fun clearReadings()
}
