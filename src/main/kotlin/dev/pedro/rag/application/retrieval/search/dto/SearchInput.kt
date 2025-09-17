package dev.pedro.rag.application.retrieval.search.dto

data class SearchInput(
    val queryText: String,
    val topK: Int,
    val filter: Map<String, String>? = null,
)
