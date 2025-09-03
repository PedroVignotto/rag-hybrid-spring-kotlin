package dev.pedro.rag.infra.llm.ollama.model.response

internal data class OllamaChatResponseMessage(
    val role: String? = null,
    val content: String? = null,
)
