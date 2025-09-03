package dev.pedro.rag.application.chat

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput

class ChatUseCase(private val llm: LlmChatPort) {
    fun handle(input: ChatInput): ChatOutput = llm.complete(input)
}
