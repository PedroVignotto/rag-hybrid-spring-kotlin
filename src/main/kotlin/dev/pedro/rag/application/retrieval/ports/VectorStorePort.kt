package dev.pedro.rag.application.retrieval.ports

import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk

interface VectorStorePort {
    fun upsert(
        collection: CollectionSpec,
        documentId: DocumentId,
        items: List<Pair<TextChunk, EmbeddingVector>>,
    )

    fun search(
        collection: CollectionSpec,
        query: EmbeddingVector,
        topK: Int,
        filter: Map<String, String>? = null,
    ): List<SearchMatch>

    fun deleteByDocumentId(
        collection: CollectionSpec,
        documentId: DocumentId,
    )

    fun count(collection: CollectionSpec): Long
}
