package dev.pedro.rag.application.retrieval.ask.context

data class CitationIndex(
    val n: Int,
    val documentId: String,
    val title: String,
    val chunkIndex: Int,
)
