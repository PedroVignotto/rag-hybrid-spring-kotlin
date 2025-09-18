package dev.pedro.rag.infra.llm.ollama.chat.response

data class OllamaChatResponse(
    val message: OllamaChatMessageResponse? = null,
    val done: Boolean? = null,
)