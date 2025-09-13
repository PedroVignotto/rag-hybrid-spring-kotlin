package dev.pedro.rag.domain.retrieval

class EmbeddingVector(
    val values: FloatArray,
    val dim: Int,
    val normalized: Boolean,
)
