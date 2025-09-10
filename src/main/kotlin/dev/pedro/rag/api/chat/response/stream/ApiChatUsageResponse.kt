package dev.pedro.rag.api.chat.response.stream

data class ApiChatUsageResponse(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalDurationMs: Long?,
    val loadDurationMs: Long?,
)
