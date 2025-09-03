package dev.pedro.rag.infra.llm.ollama.model.request

internal data class OllamaChatOptions(
    val temperature: Double? = null,
    val topP: Double? = null,
    val numPredict: Int? = null,
    val seed: Int? = null,
)
