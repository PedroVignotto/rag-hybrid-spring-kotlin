package dev.pedro.rag.api.retrieval.response

data class IngestResponse(
    val documentId: String,
    val chunksIngested: Int,
)