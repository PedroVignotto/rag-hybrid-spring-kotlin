package dev.pedro.rag.config.retrieval

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("retrieval")
data class RetrievalProperties(
    val embedding: Embedding
) {
    data class Embedding(
        val provider: String,
        val model: String,
        val dimension: Int,
        val normalized: Boolean
    )
}