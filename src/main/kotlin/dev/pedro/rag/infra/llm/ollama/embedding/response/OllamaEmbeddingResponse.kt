package dev.pedro.rag.infra.llm.ollama.embedding.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OllamaEmbeddingResponse(
    val embedding: List<Double>? = null,
    val embeddings: List<List<Double>>? = null,
    val error: String? = null,
    val model: String? = null,
)
