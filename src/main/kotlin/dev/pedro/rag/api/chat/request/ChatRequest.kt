package dev.pedro.rag.api.chat.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class ChatRequest(
    @field:NotEmpty
    val messages: List<@Valid ChatMessageRequest>,
    val params: ChatParamsRequest? = null,
)

data class ChatMessageRequest(
    @field:NotBlank
    val role: String,
    @field:NotBlank
    val content: String,
)

data class ChatParamsRequest(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
)
