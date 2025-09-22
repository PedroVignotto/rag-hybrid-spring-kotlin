package dev.pedro.rag.application.retrieval.ports

import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk

interface TextIndexPort {
    fun index(
        documentId: DocumentId,
        chunks: List<TextChunk>,
    ): Int

    fun delete(documentId: DocumentId): Int

    fun search(
        query: String,
        width: Int,
    ): List<SearchMatch>

    fun size(): Int
}
