package dev.pedro.rag.infra.llm.ollama.mappers

import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatMessageRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatOptionsRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequest

fun ChatInput.toOllamaChatRequest(model: String): OllamaChatRequest =
    OllamaChatRequest(
        model = model,
        messages = this.messages.map { it.toOllamaChatMessageRequest() },
        options =
            OllamaChatOptionsRequest(
                temperature = this.params.temperature,
                topP = this.params.topP,
                numPredict = this.params.maxTokens,
            ),
    )

private fun ChatMessage.toOllamaChatMessageRequest(): OllamaChatMessageRequest =
    OllamaChatMessageRequest(
        role = this.role.toOllamaRole(),
        content = this.content,
    )

private fun ChatRole.toOllamaRole(): String =
    when (this) {
        ChatRole.SYSTEM -> "system"
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
    }
