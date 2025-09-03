package dev.pedro.rag.infra.llm.ollama.model.request

internal data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatRequestMessage>,
    val stream: Boolean = false,
    val keepAlive: String = "0s",
    val options: OllamaChatOptions? = null,
)
