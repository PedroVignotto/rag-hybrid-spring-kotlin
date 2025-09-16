package dev.pedro.rag.application.retrieval.usecase

import dev.pedro.rag.application.retrieval.ingest.dto.IngestInput
import dev.pedro.rag.application.retrieval.ingest.dto.IngestOutput
import dev.pedro.rag.application.retrieval.ports.Chunker
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.TextChunk

class DefaultIngestUseCase(
    private val chunker: Chunker,
    private val embedPort: EmbedPort,
    private val vectorStorePort: VectorStorePort,
) : IngestUseCase {
    override fun ingest(input: IngestInput): IngestOutput {
        validateInput(input)
        val embeddingSpec: EmbeddingSpec = embedPort.spec()
        val collectionSpec = CollectionSpec(embeddingSpec.provider, embeddingSpec.model, embeddingSpec.dim)
        val rawChunks: List<TextChunk> = createChunks(input)
        if (rawChunks.isEmpty()) {
            return IngestOutput(documentId = input.documentId, chunksIngested = 0)
        }
        val chunksWithMetadata = mergeBaseAndChunkMetadata(input.baseMetadata, rawChunks)
        val embeddings: List<EmbeddingVector> = embedPort.embedAll(chunksWithMetadata.map { it.text })
        validateEmbeddingDimensions(embeddings, embeddingSpec)
        vectorStorePort.upsert(
            collection = collectionSpec,
            documentId = input.documentId,
            items = chunksWithMetadata.zip(embeddings),
        )
        return IngestOutput(documentId = input.documentId, chunksIngested = chunksWithMetadata.size)
    }

    private fun validateInput(input: IngestInput) {
        require(input.text.isNotBlank()) { "text must not be blank." }
        require(input.chunkSize > 0) { "chunkSize must be > 0." }
        require(input.overlap in 0 until input.chunkSize) {
            "overlap must be in [0, chunkSize - 1]."
        }
    }

    private fun createChunks(input: IngestInput): List<TextChunk> = chunker.split(input.text, input.chunkSize, input.overlap)

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
