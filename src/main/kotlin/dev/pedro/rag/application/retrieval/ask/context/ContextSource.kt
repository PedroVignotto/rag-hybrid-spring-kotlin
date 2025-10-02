package dev.pedro.rag.application.retrieval.ask.context

data class ContextSource(
    val documentId: String,
    val title: String,
    val chunkIndex: Int,
    val text: String,
    val score: Double,
)
