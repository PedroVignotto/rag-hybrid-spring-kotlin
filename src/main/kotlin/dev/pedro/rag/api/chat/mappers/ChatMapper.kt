package dev.pedro.rag.api.chat.mappers

import dev.pedro.rag.api.chat.request.ChatMessageRequest
import dev.pedro.rag.api.chat.request.ChatParamsRequest
import dev.pedro.rag.api.chat.request.ChatRequest
import dev.pedro.rag.api.chat.response.ChatResponse
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.domain.chat.InferenceParams

fun ChatRequest.toDomain(): ChatInput =
    ChatInput(
        messages = this.messages.map { it.toDomain() },
        params = this.params.toDomain(),
    )

private fun ChatMessageRequest.toDomain(): ChatMessage =
    ChatMessage(
        role = this.role.toDomain(),
        content = this.content,
    )

private fun ChatParamsRequest?.toDomain(): InferenceParams =
    InferenceParams().let { default ->
        InferenceParams(
            temperature = this?.temperature ?: default.temperature,
            topP       = this?.topP       ?: default.topP,
            maxTokens  = this?.maxTokens  ?: default.maxTokens,
        )
    }

private fun String.toDomain(): ChatRole =
    when (this.trim().lowercase()) {
        "system" -> ChatRole.SYSTEM
        "user" -> ChatRole.USER
        "assistant" -> ChatRole.ASSISTANT
        else -> throw IllegalArgumentException("Invalid role: '$this' (expected system|user|assistant)")
    }

fun ChatOutput.toResponse(): ChatResponse =
    ChatResponse(content = this.content)
