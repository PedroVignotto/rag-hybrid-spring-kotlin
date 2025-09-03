package dev.pedro.rag.infra.llm.ollama.model.request

internal data class OllamaChatRequestMessage(
    val role: String,
    val content: String,
)
