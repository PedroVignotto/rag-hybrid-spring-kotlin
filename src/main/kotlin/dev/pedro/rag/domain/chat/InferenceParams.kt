package dev.pedro.rag.domain.chat

data class InferenceParams(
    val temperature: Double? = 0.2,
    val topP: Double? = 0.9,
    val maxTokens: Int? = 512,
    val keepAlive: String = "0s"
)
