package dev.pedro.rag.application.chat.usecase

import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatUsage

interface ChatUseCase {
    fun handle(input: ChatInput): ChatOutput

    fun handleStream(
        input: ChatInput,
        onDelta: (String) -> Unit,
        onUsage: ((ChatUsage) -> Unit)? = null,
    )
}
