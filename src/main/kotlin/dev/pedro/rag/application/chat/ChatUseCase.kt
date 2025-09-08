package dev.pedro.rag.application.chat

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatUsage

class ChatUseCase(private val llm: LlmChatPort) {
    fun handle(input: ChatInput): ChatOutput = llm.complete(input)

    fun handleStream(
        input: ChatInput,
        onDelta: (String) -> Unit,
        onUsage: ((ChatUsage) -> Unit)? = null,
    ) = llm.stream(input, onDelta, onUsage)
}
