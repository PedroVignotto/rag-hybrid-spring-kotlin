package dev.pedro.rag.infra.llm.ollama.mappers

import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatOptions
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequestMessage

object ChatToOllamaMapper {
    fun toRequest(
        input: ChatInput,
        model: String,
    ): OllamaChatRequest =
        OllamaChatRequest(
            model = model,
            messages = input.messages.map { it.toRequestMessage() },
            options =
                OllamaChatOptions(
                    temperature = input.params.temperature,
                    topP = input.params.topP,
                    numPredict = input.params.maxTokens,
                ),
        )

    private fun ChatMessage.toRequestMessage(): OllamaChatRequestMessage =
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
}
