package dev.pedro.rag.application.retrieval.ask.dto

data class AskOutput(
    val answer: String,
    val citations: List<Citation> = emptyList(),
    val usedK: Int,
    val notes: String? = null,
)

data class Citation(
    val documentId: String,
    val title: String,
    val chunkIndex: Int,
)
