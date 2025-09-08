package dev.pedro.rag.domain.chat

data class ChatUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalDurationMs: Long?,
    val loadDurationMs: Long?,
)
