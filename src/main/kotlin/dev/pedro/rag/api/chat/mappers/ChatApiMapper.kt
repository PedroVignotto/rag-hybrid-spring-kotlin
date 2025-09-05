package dev.pedro.rag.api.chat.mappers

import dev.pedro.rag.api.chat.request.ApiChatMessage
import dev.pedro.rag.api.chat.request.ApiChatParams
import dev.pedro.rag.api.chat.request.ApiChatRequest
import dev.pedro.rag.api.chat.response.ApiChatResponse
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.domain.chat.InferenceParams

fun ApiChatRequest.toDomain(): ChatInput =
    ChatInput(
        messages = this.messages.map { it.toDomain() },
        params = this.params.toDomainParams(),
    )

private fun ApiChatMessage.toDomain(): ChatMessage =
    ChatMessage(
        role = this.role.toDomainRole(),
        content = this.content,
    )

private fun ApiChatParams?.toDomainParams(): InferenceParams =
    if (this == null) {
        InferenceParams(temperature = null, topP = null, maxTokens = null)
    } else {
        InferenceParams(
            temperature = this.temperature,
            topP = this.topP,
            maxTokens = this.maxTokens,
        )
    }

private fun String.toDomainRole(): ChatRole =
    when (this.trim().lowercase()) {
        "system" -> ChatRole.SYSTEM
        "user" -> ChatRole.USER
        "assistant" -> ChatRole.ASSISTANT
        else -> throw IllegalArgumentException("Invalid role: '$this' (expected system|user|assistant)")
    }

fun ChatOutput.toApi(): ApiChatResponse = ApiChatResponse(content = this.content)
