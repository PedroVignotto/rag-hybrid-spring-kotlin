package dev.pedro.rag.application.chat.ports

import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput

interface LlmChatPort {
    fun complete(input: ChatInput): ChatOutput
}
