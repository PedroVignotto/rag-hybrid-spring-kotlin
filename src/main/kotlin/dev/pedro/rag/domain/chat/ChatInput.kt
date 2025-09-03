package dev.pedro.rag.domain.chat

data class ChatInput(
    val messages: List<ChatMessage>,
    val params: InferenceParams = InferenceParams()
)
