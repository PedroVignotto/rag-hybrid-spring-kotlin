package dev.pedro.rag.application.retrieval.delete.usecase

import dev.pedro.rag.application.retrieval.delete.dto.DeleteOutput
import dev.pedro.rag.domain.retrieval.DocumentId

interface DeleteUseCase {
    fun handle(documentId: DocumentId): DeleteOutput
}
