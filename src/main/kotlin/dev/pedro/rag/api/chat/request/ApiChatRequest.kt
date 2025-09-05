package dev.pedro.rag.api.chat.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

data class ApiChatRequest(
    @field:NotEmpty
    val messages: List<@Valid ApiChatMessage>,
    val params: ApiChatParams? = null,
)
