package dev.pedro.rag.infra.llm.ollama.model.response

data class OllamaChatResponseMessage(
    val role: String? = null,
    val content: String? = null,
)
