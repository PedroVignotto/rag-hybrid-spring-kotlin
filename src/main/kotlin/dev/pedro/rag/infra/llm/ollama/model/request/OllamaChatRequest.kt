package dev.pedro.rag.infra.llm.ollama.model.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatRequestMessage>,
    val stream: Boolean? = null,
    val options: OllamaChatOptions? = null,
    val keepAlive: String? = null,
)
