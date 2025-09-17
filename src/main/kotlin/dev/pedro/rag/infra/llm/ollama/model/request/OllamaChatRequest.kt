package dev.pedro.rag.infra.llm.ollama.model.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessageRequest>,
    val stream: Boolean? = null,
    val options: OllamaChatOptionsRequest? = null,
    val keepAlive: String? = null,
)

data class OllamaChatMessageRequest(
    val role: String,
    val content: String,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class OllamaChatOptionsRequest(
    val temperature: Double? = null,
    val topP: Double? = null,
    val numPredict: Int? = null,
    val seed: Int? = null,
)
