package dev.pedro.rag.infra.llm.ollama.embedding.request

data class OllamaEmbeddingRequest(
    val model: String,
    val input: List<String>,
)
