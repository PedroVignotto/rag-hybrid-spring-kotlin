package dev.pedro.rag.api.chat.request

import jakarta.validation.constraints.NotBlank

data class ApiChatMessage(
    @field:NotBlank
    val role: String,
    @field:NotBlank
    val content: String,
)
