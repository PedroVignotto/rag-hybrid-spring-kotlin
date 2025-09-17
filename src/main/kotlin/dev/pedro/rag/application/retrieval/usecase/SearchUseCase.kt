package dev.pedro.rag.application.retrieval.usecase

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.search.dto.SearchInput
import dev.pedro.rag.application.retrieval.search.dto.SearchOutput
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.CollectionSpec.Companion.fromSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch

class SearchUseCase(
    private val embedPort: EmbedPort,
    private val vectorStorePort: VectorStorePort,
) {
    fun search(input: SearchInput): SearchOutput {
        validateInput(input)
        val collectionSpec: CollectionSpec = fromSpec(embedPort.spec())
        val queryVector: EmbeddingVector = embedQueryVector(input.queryText)
        val matches: List<SearchMatch> =
            searchInStore(
                collectionSpec = collectionSpec,
                queryVector = queryVector,
                topK = input.topK,
                filter = input.filter,
            )
        return SearchOutput(matches = matches)
    }

    private fun validateInput(input: SearchInput) {
        require(input.queryText.isNotBlank()) { "query must not be blank." }
        require(input.topK > 0) { "topK must be > 0." }
    }

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
