package dev.pedro.rag.application.chat.usecase

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatUsage

class DefaultChatUseCase(private val llm: LlmChatPort) : ChatUseCase {
    override fun handle(input: ChatInput): ChatOutput = llm.complete(input)

    override fun handleStream(
        input: ChatInput,
        onDelta: (String) -> Unit,
        onUsage: ((ChatUsage) -> Unit)?,
    ) = llm.stream(input, onDelta, onUsage)
}
