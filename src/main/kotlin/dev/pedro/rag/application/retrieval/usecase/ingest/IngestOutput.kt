package dev.pedro.rag.application.retrieval.usecase.ingest

import dev.pedro.rag.domain.retrieval.DocumentId

data class IngestOutput(
    val documentId: DocumentId,
    val chunksIngested: Int,
)
