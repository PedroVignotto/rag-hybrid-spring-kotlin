package dev.pedro.rag.infra.llm.ollama.chat.response

data class OllamaChatMessageResponse(
    val role: String? = null,
    val content: String? = null,
)
