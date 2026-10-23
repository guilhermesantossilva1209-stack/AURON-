package br.com.auron.domain

import kotlinx.coroutines.flow.Flow

/** Local data contract. Implementations must never send records to an AI provider. */
interface AuronRepository {
    val tasks: Flow<List<Task>>
    val routineItems: Flow<List<RoutineItem>>
    val routineCompletions: Flow<List<RoutineCompletion>>
    val messages: Flow<List<ChatMessage>>
    val memories: Flow<List<MemoryRecord>>
    val readings: Flow<List<DailyReading>>

    suspend fun initialize()
    suspend fun saveTask(task: Task)
    suspend fun deleteTask(id: String)
    suspend fun setTaskCompleted(id: String, completed: Boolean, now: Long)
    suspend fun saveRoutine(item: RoutineItem)
    suspend fun deleteRoutine(id: String)
    suspend fun setRoutineCompleted(item: RoutineItem, day: String, completed: Boolean, now: Long)
    suspend fun addMessage(message: ChatMessage)
    suspend fun clearMessages()
    suspend fun clearAll()
    suspend fun saveMemory(memory: MemoryRecord)
    suspend fun deleteMemory(id: String)
    suspend fun getOrCreateReading(day: String, verseIds: List<String>): DailyReading
    /** Explicit copy only; preserves the local profile and user-edited account records. */
    suspend fun importLegacyData(): Boolean
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(settings: AppSettings)
    suspend fun reset()
}
