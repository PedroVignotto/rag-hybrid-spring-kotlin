package dev.pedro.rag.api.chat.request

data class ApiChatParams(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
)
