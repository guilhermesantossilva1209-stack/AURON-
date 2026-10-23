package br.com.auron.domain

/** Limits shared by entry points; they bound local storage and command parsing. */
object InputLimits {
    const val MESSAGE = 4_000
    const val RESPONSE = 16_000
    const val TITLE = 160
    const val USER_NAME = 60
}

enum class Period { MORNING, NIGHT }

data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val priority: Int = 1,
    val dueDate: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
)

data class RoutineItem(
    val id: String,
    val title: String,
    val period: Period,
    val orderIndex: Int,
    val action: String? = null,
)

/** A snapshot preserves the history even when its routine item changes. */
data class RoutineCompletion(
    val itemId: String,
    val day: String,
    val title: String,
    val period: Period,
    val completedAt: Long,
)

data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val createdAt: Long,
    val source: String = "text",
)

data class AppSettings(
    val userName: String = "comandante",
    val speakReplies: Boolean = false,
    val speechRate: Float = 1f,
    val allowOnlineSpeech: Boolean = false,
    val humor: Int = 95,
    val sarcasm: Int = 60,
    val formality: Int = 55,
    val proactivity: Int = 90,
    val useRemoteAi: Boolean = false,
    val memoryEnabled: Boolean = false,
    val shareHistoryWithAi: Boolean = false,
    val shareMemoryWithAi: Boolean = false,
    val showIntro: Boolean = true,
    val reducedMotion: Boolean = false,
    val speechPitch: Float = 1f,
    val speechVoiceName: String = "",
    val speechVoiceEngine: String = "",
)

/** Explicit user-managed memory; the persistent representation is encrypted. */
data class MemoryRecord(val id: String, val title: String, val content: String, val updatedAt: Long)

/** Stable verse identifiers, not regenerated selections when revisiting the same day. */
data class DailyReading(val day: String, val verseIds: List<String>)
