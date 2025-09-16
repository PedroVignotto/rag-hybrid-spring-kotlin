package dev.pedro.rag.application.retrieval.usecase

import dev.pedro.rag.application.retrieval.ingest.dto.IngestInput
import dev.pedro.rag.application.retrieval.ingest.dto.IngestOutput

interface IngestUseCase {
    fun ingest(input: IngestInput): IngestOutput
}
