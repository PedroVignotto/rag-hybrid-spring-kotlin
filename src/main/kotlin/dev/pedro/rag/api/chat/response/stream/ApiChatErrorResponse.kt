package dev.pedro.rag.api.chat.response.stream

data class ApiChatErrorResponse(
    val status: Int,
    val upstreamBody: String? = null,
    val message: String? = null
)