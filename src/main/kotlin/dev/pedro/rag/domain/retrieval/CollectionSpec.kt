package dev.pedro.rag.domain.retrieval

data class CollectionSpec(
    val provider: String,
    val model: String,
    val dim: Int,
) {
    companion object {
        fun fromSpec(spec: EmbeddingSpec): CollectionSpec = CollectionSpec(provider = spec.provider, model = spec.model, dim = spec.dim)
    }
}
