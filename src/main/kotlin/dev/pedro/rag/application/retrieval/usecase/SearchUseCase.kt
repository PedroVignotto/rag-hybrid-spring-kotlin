package dev.pedro.rag.application.retrieval.usecase

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch

class SearchUseCase(
    private val embedPort: EmbedPort,
    private val vectorStorePort: VectorStorePort,
) {
    fun search(
        queryText: String,
        topK: Int,
        filter: Map<String, String>?,
    ): List<SearchMatch> {
        validateInputs(queryText, topK)
        val collectionSpec: CollectionSpec = toCollectionSpec(embedPort.spec())
        val queryVector: EmbeddingVector = embedQueryVector(queryText)
        return searchInStore(collectionSpec, queryVector, topK, filter)
    }

    private fun validateInputs(
        queryText: String,
        topK: Int,
    ) {
        require(queryText.isNotBlank()) { "query must not be blank." }
        require(topK > 0) { "topK must be > 0." }
    }

    private fun toCollectionSpec(spec: EmbeddingSpec): CollectionSpec =
        CollectionSpec(provider = spec.provider, model = spec.model, dim = spec.dim)

    private fun embedQueryVector(queryText: String): EmbeddingVector = embedPort.embed(queryText)

    private fun searchInStore(
        collectionSpec: CollectionSpec,
        queryVector: EmbeddingVector,
        topK: Int,
        filter: Map<String, String>?,
    ): List<SearchMatch> =
        vectorStorePort.search(
            collection = collectionSpec,
            query = queryVector,
            topK = topK,
            filter = filter,
        )
}
