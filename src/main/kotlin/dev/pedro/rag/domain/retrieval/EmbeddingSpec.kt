package dev.pedro.rag.domain.retrieval

data class EmbeddingSpec(
    val provider: String,
    val model: String,
    val dim: Int,
    val normalized: Boolean,
)
