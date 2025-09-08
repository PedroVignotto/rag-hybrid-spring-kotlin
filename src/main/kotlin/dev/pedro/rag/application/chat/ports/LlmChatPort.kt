package dev.pedro.rag.application.chat.ports

import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatUsage

interface LlmChatPort {
    fun complete(input: ChatInput): ChatOutput

    fun stream(
        input: ChatInput,
        onDelta: (String) -> Unit,
        onUsage: ((ChatUsage) -> Unit)? = null,
    )
}
