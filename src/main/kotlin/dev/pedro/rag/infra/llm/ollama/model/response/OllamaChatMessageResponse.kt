package dev.pedro.rag.infra.llm.ollama.model.response

data class OllamaChatMessageResponse(
    val role: String? = null,
    val content: String? = null,
)
