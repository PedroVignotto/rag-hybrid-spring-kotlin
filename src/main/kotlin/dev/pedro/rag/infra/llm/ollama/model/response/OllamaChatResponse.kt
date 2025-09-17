package dev.pedro.rag.infra.llm.ollama.model.response

data class OllamaChatResponse(
    val message: OllamaChatMessageResponse? = null,
    val done: Boolean? = null,
)
