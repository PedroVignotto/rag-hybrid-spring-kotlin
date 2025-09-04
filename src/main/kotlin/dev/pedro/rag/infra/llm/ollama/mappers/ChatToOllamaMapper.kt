package dev.pedro.rag.infra.llm.ollama.mappers

import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatOptions
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequestMessage

fun ChatInput.toOllamaChatRequest(model: String): OllamaChatRequest =
    OllamaChatRequest(
        model = model,
        messages = this.messages.map { it.toOllamaRequestMessage() },
        options =
            OllamaChatOptions(
                temperature = this.params.temperature,
                topP = this.params.topP,
                numPredict = this.params.maxTokens,
            ),
    )

private fun ChatMessage.toOllamaRequestMessage(): OllamaChatRequestMessage =
    OllamaChatRequestMessage(
        role = this.role.toOllamaRole(),
        content = this.content,
    )

private fun ChatRole.toOllamaRole(): String =
    when (this) {
        ChatRole.SYSTEM -> "system"
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
    }
