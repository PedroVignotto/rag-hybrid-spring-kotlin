package dev.pedro.rag.infra.llm.ollama

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.infra.llm.ollama.client.OllamaClient
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.mappers.toOllamaChatRequest

class OllamaChatProvider(
    private val client: OllamaClient,
    private val defaultModel: String,
) : LlmChatPort {
    override fun complete(input: ChatInput): ChatOutput {
        val request = input.toOllamaChatRequest(model = defaultModel)
        val response = client.chat(request)
        val content =
            response.message?.content
                ?.takeIf { it.isNotBlank() }
                ?: throw OllamaInvalidResponseException("Ollama response is missing `message.content`")
        return ChatOutput(content = content)
    }
}
