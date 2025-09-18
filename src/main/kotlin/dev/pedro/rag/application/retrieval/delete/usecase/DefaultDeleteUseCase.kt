package dev.pedro.rag.application.retrieval.delete.usecase

import dev.pedro.rag.application.retrieval.delete.dto.DeleteOutput
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.DocumentId

class DefaultDeleteUseCase(
    private val vectorStore: VectorStorePort,
    private val activeCollection: CollectionSpec,
) : DeleteUseCase {
    override fun handle(documentId: DocumentId): DeleteOutput {
        return DeleteOutput(deleted = vectorStore.deleteByDocumentId(activeCollection, documentId))
    }
}
