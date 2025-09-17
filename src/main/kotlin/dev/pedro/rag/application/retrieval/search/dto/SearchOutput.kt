package dev.pedro.rag.application.retrieval.search.dto

import dev.pedro.rag.domain.retrieval.SearchMatch

data class SearchOutput(
    val matches: List<SearchMatch>,
)
