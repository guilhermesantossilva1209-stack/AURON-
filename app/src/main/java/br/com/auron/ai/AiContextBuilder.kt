package br.com.auron.ai

import br.com.auron.domain.AiRequest

data class ContextMessage(val role: String, val text: String)
data class ContextMemory(val title: String, val content: String)
data class AiContext(
    val history: List<ContextMessage>,
    val memories: List<ContextMemory>,
    val shareHistory: Boolean,
    val shareMemory: Boolean,
)

/** Pure Kotlin privacy boundary. No IDs, timestamps, names or tokens are exported. */
object AiContextBuilder {
    const val HISTORY_COUNT = 12
    const val HISTORY_TEXT = 2_000
    const val MEMORY_COUNT = 6
    const val MEMORY_TEXT = 1_000
    const val TOTAL_CONTEXT_CHARS = 10_000

    fun build(request: AiRequest): AiContext {
        val historyAllowed = request.settings.useRemoteAi && request.settings.shareHistoryWithAi
        val memoryAllowed = request.settings.useRemoteAi && request.settings.memoryEnabled && request.settings.shareMemoryWithAi
        var budget = TOTAL_CONTEXT_CHARS
        val memories = if (memoryAllowed) request.memories.sortedByDescending { it.updatedAt }
            .take(MEMORY_COUNT).mapNotNull { memory ->
                val title = memory.title.take(160)
                val content = memory.content.take(minOf(MEMORY_TEXT, (budget - title.length).coerceAtLeast(0)))
                if (title.isBlank() || content.isBlank()) null else {
                    budget -= title.length + content.length
                    ContextMemory(title, content)
                }
            } else emptyList()
        val history = if (historyAllowed) request.history.asReversed()
            .filter { it.role in setOf("user", "assistant") && it.text.isNotBlank() }
            .take(HISTORY_COUNT).mapNotNull { message ->
                val text = message.text.take(minOf(HISTORY_TEXT, budget))
                if (text.isBlank()) null else { budget -= text.length; ContextMessage(message.role, text) }
            }.asReversed() else emptyList()
        return AiContext(history, memories, historyAllowed, memoryAllowed)
    }
}
