package dev.pedro.rag.infra.llm.ollama.chat.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class OllamaChatStreamChunkResponse(
    val model: String? = null,
    val createdAt: String? = null,
    val message: OllamaChatMessageResponse? = null,
    val done: Boolean = false,
    val response: String? = null,
    val totalDuration: Long? = null,
    val loadDuration: Long? = null,
    val promptEvalCount: Int? = null,
    val evalCount: Int? = null,
)