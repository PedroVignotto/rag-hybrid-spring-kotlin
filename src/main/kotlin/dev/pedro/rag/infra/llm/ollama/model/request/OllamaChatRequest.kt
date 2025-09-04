package dev.pedro.rag.infra.llm.ollama.model.request

data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatRequestMessage>,
    val stream: Boolean = false,
    val options: OllamaChatOptions? = null,
)
