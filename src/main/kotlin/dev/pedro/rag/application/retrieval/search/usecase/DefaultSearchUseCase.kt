package dev.pedro.rag.application.retrieval.search.usecase

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.TextIndexPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.search.dto.SearchInput
import dev.pedro.rag.application.retrieval.search.dto.SearchOutput
import dev.pedro.rag.application.retrieval.search.ranking.HybridSearchAggregator
import dev.pedro.rag.config.retrieval.RetrievalSearchProperties
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch

class DefaultSearchUseCase(
    private val embedPort: EmbedPort,
    private val vectorStorePort: VectorStorePort,
    private val textIndexPort: TextIndexPort,
    private val props: RetrievalSearchProperties,
    private val aggregator: HybridSearchAggregator,
) : SearchUseCase {
    override fun search(input: SearchInput): SearchOutput {
        validateInput(input)
        val collection = CollectionSpec.fromSpec(embedPort.spec())
        val queryVector = embedQuery(input.queryText)
        val vectorHits = searchVector(collection, queryVector, input.filter)
        val bm25Hits = searchBm25(input.queryText, input.filter)
        val fused = aggregate(vectorHits, bm25Hits, input.topK)
        return SearchOutput(matches = fused)
    }

    private fun validateInput(input: SearchInput) {
        require(input.queryText.isNotBlank()) { "query must not be blank." }
        require(input.topK > 0) { "topK must be > 0." }
    }

    private fun embedQuery(queryText: String): EmbeddingVector = embedPort.embed(queryText)

    private fun searchVector(
        collection: CollectionSpec,
        queryVector: EmbeddingVector,
        filter: Map<String, String>?,
    ): List<SearchMatch> =
        if (props.vector.enabled) {
            vectorStorePort.search(collection = collection, query = queryVector, topK = props.vector.width, filter = filter)
        } else {
            emptyList()
        }

    private fun searchBm25(
        queryText: String,
        filter: Map<String, String>?,
    ): List<SearchMatch> =
        if (props.bm25.enabled) {
            textIndexPort.search(query = queryText, width = props.bm25.width, filter = filter)
        } else {
            emptyList()
        }

    private fun aggregate(
        vectorHits: List<SearchMatch>,
        bm25Hits: List<SearchMatch>,
        k: Int,
    ): List<SearchMatch> =
        aggregator.aggregate(
            vectorHits = vectorHits,
            bm25Hits = bm25Hits,
            k = k,
        )
}
