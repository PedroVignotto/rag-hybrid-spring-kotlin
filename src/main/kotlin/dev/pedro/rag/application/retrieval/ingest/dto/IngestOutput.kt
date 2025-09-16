package dev.pedro.rag.application.retrieval.ingest.dto

import dev.pedro.rag.domain.retrieval.DocumentId

data class IngestOutput(
    val documentId: DocumentId,
    val chunksIngested: Int,
)
