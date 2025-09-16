package dev.pedro.rag.application.retrieval.usecase

import dev.pedro.rag.application.retrieval.ports.Chunker
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.usecase.ingest.IngestInput
import dev.pedro.rag.application.retrieval.usecase.ingest.IngestResult
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.TextChunk

class IngestUseCase(
    private val chunker: Chunker,
    private val embedPort: EmbedPort,
    private val vectorStorePort: VectorStorePort,
) {
    fun ingest(command: IngestInput): IngestResult {
        validateCommand(command)
        val embeddingSpec: EmbeddingSpec = embedPort.spec()
        val collectionSpec = CollectionSpec(embeddingSpec.provider, embeddingSpec.model, embeddingSpec.dim)
        val rawChunks: List<TextChunk> = createChunks(command)
        if (rawChunks.isEmpty()) {
            return IngestResult(documentId = command.documentId, chunksIngested = 0)
        }
        val chunksWithMetadata = mergeBaseAndChunkMetadata(command.baseMetadata, rawChunks)
        val embeddings: List<EmbeddingVector> = embedPort.embedAll(chunksWithMetadata.map { it.text })
        validateEmbeddingDimensions(embeddings, embeddingSpec)
        vectorStorePort.upsert(
            collection = collectionSpec,
            documentId = command.documentId,
            items = chunksWithMetadata.zip(embeddings),
        )
        return IngestResult(documentId = command.documentId, chunksIngested = chunksWithMetadata.size)
    }

    private fun validateCommand(command: IngestInput) {
        require(command.text.isNotBlank()) { "text must not be blank." }
        require(command.chunkSize > 0) { "chunkSize must be > 0." }
        require(command.overlap in 0 until command.chunkSize) {
            "overlap must be in [0, chunkSize - 1]."
        }
    }

    private fun createChunks(command: IngestInput): List<TextChunk> = chunker.split(command.text, command.chunkSize, command.overlap)

    private fun mergeBaseAndChunkMetadata(
        baseMetadata: Map<String, String>,
        rawChunks: List<TextChunk>,
    ): List<TextChunk> =
        rawChunks.map { chunk ->
            val merged = baseMetadata + chunk.metadata
            TextChunk(text = chunk.text, metadata = merged)
        }

    private fun validateEmbeddingDimensions(
        vectors: List<EmbeddingVector>,
        spec: EmbeddingSpec,
    ) {
        val hasMismatch = vectors.any { it.dim != spec.dim }
        require(!hasMismatch) {
            "All embedding vectors must have dim=${spec.dim} (provider=${spec.provider}, model=${spec.model})."
        }
    }
}
