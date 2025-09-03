package dev.pedro.rag.infra.llm.ollama.model.response

internal data class OllamaChatResponse(
    val message: OllamaChatResponseMessage? = null,
    val done: Boolean? = null,
)
