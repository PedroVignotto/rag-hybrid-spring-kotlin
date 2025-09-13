package dev.pedro.rag.application.retrieval.ports

import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector

interface EmbedPort {
    fun embed(text: String): EmbeddingVector

    fun embedAll(texts: List<String>): List<EmbeddingVector>

    fun spec(): EmbeddingSpec
}
