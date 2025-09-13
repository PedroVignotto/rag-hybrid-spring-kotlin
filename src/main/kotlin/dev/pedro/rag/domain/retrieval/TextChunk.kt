package dev.pedro.rag.domain.retrieval

data class TextChunk(
    val text: String,
    val metadata: Map<String, String> = emptyMap(),
)
