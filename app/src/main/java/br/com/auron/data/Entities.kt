package br.com.auron.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.auron.domain.ChatMessage
import br.com.auron.domain.Period
import br.com.auron.domain.RoutineCompletion
import br.com.auron.domain.RoutineItem
import br.com.auron.domain.Task
import br.com.auron.domain.DailyReading
import org.json.JSONArray

@Entity(tableName = "tasks", indices = [Index("completedAt")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val priority: Int,
    val dueDate: String?,
    val createdAt: Long,
    val completedAt: Long?,
)

@Entity(tableName = "routine_items", indices = [Index(value = ["period", "orderIndex"])])
data class RoutineItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val period: String,
    val orderIndex: Int,
    val action: String? = null,
)

/** Deliberately no foreign key: a deleted or renamed routine must retain its history. */
@Entity(tableName = "routine_completions", primaryKeys = ["itemId", "day"], indices = [Index("day")])
data class RoutineCompletionEntity(
    val itemId: String,
    val day: String,
    val title: String,
    val period: String,
    val completedAt: Long,
)

@Entity(tableName = "chat_messages", indices = [Index("createdAt")])
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val role: String,
    val text: String,
    val createdAt: Long,
    val source: String,
)

/** Retained after clearAll to keep an intentionally empty routine empty. */
@Entity(tableName = "database_metadata")
data class MetadataEntity(@PrimaryKey val key: String, val value: String)

/** Titles and content share an encrypted payload; only ID and timestamp are plaintext. */
@Entity(tableName = "memories", indices = [Index("updatedAt")])
data class MemoryEntity(
    @PrimaryKey val id: String,
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val updatedAt: Long,
)

@Entity(tableName = "daily_readings")
data class DailyReadingEntity(@PrimaryKey val day: String, val verseIdsJson: String)

internal fun TaskEntity.toDomain() = Task(id, title, notes, priority, dueDate, createdAt, completedAt)
internal fun Task.toEntity() = TaskEntity(id, title, notes, priority, dueDate, createdAt, completedAt)
internal fun RoutineItemEntity.toDomain() = RoutineItem(id, title, Period.valueOf(period), orderIndex, action)
internal fun RoutineItem.toEntity() = RoutineItemEntity(id, title, period.name, orderIndex, action)
internal fun RoutineCompletionEntity.toDomain() =
    RoutineCompletion(itemId, day, title, Period.valueOf(period), completedAt)
internal fun ChatMessageEntity.toDomain() = ChatMessage(id, role, text, createdAt, source)
internal fun ChatMessage.toEntity() = ChatMessageEntity(id, role, text, createdAt, source)
internal fun DailyReadingEntity.toDomain(): DailyReading {
    val ids = JSONArray(verseIdsJson)
    return DailyReading(day, List(ids.length()) { ids.getString(it) })
}
