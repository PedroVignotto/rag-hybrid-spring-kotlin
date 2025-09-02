package dev.pedro.rag.domain.chat

data class ChatMessage(val role: ChatRole, val content: String)