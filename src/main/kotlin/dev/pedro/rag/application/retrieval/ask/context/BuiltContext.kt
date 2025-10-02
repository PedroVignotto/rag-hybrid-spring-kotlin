package dev.pedro.rag.application.retrieval.ask.context

data class BuiltContext(
    val text: String,
    val index: List<CitationIndex>,
    val usedK: Int,
    val truncated: Boolean,
)
