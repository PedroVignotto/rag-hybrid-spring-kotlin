package dev.pedro.rag.domain.retrieval

data class SearchMatch(
    val documentId: DocumentId,
    val chunk: TextChunk,
    val score: Double,
)
