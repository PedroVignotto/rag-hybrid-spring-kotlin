package dev.pedro.rag.infra.llm.ollama.model.request

data class OllamaChatRequestMessage(
    val role: String,
    val content: String,
)
