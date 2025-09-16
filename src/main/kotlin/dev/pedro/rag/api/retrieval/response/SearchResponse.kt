package dev.pedro.rag.api.retrieval.response

data class SearchResponse(
    val matches: List<SearchMatchView>,
)

data class SearchMatchView(
    val documentId: String,
    val text: String,
    val score: Double,
    val metadata: Map<String, String>,
)