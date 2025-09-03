package dev.pedro.rag.infra.llm.ollama.model.request

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class OllamaChatOptions(
    val temperature: Double? = null,
    val topP: Double? = null,
    val numPredict: Int? = null,
    val seed: Int? = null,
)
