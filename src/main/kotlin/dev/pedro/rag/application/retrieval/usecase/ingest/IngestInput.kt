package dev.pedro.rag.application.retrieval.usecase.ingest

import dev.pedro.rag.domain.retrieval.DocumentId

data class IngestInput(
    val documentId: DocumentId,
    val text: String,
    val baseMetadata: Map<String, String>,
    val chunkSize: Int,
    val overlap: Int,
)
