package dev.pedro.rag.infra.llm.ollama.model.response

data class OllamaChatResponse(
    val message: OllamaChatResponseMessage? = null,
    val done: Boolean? = null,
)
