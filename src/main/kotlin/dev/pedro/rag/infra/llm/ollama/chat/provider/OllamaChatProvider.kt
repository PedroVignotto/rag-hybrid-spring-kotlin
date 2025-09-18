package dev.pedro.rag.infra.llm.ollama.chat.provider

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatUsage
import dev.pedro.rag.infra.llm.ollama.chat.mappers.toOllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.client.OllamaClient
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import java.util.concurrent.TimeUnit

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

    override fun stream(
        input: ChatInput,
        onDelta: (String) -> Unit,
        onUsage: ((ChatUsage) -> Unit)?,
    ) {
        val request = input.toOllamaChatRequest(model = defaultModel)
        client.chatStream(
            payload = request,
            onDelta = onDelta,
            onDoneChunk = { chunk ->
                onUsage?.invoke(
                    ChatUsage(
                        promptTokens = chunk.promptEvalCount,
                        completionTokens = chunk.evalCount,
                        totalDurationMs = chunk.totalDuration.nanosToMillis(),
                        loadDurationMs = chunk.loadDuration.nanosToMillis(),
                    ),
                )
            },
        )
    }

    private fun Long?.nanosToMillis(): Long? = this?.let(TimeUnit.NANOSECONDS::toMillis)
}