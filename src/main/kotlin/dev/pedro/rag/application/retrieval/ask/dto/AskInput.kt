package dev.pedro.rag.application.retrieval.ask.dto

data class AskInput(
    val query: String,
    val topK: Int = 10,
    val filter: Map<String, String> = emptyMap(),
    val lang: String? = null,
)
