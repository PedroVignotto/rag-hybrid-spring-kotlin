package dev.pedro.rag.application.retrieval.search.usecase

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.TextIndexPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.search.dto.SearchInput
import dev.pedro.rag.application.retrieval.search.ranking.HybridSearchAggregator
import dev.pedro.rag.application.retrieval.search.ranking.MmrReRanker
import dev.pedro.rag.application.retrieval.search.ranking.SoftDedupFilter
import dev.pedro.rag.config.retrieval.RetrievalSearchProperties
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@ExtendWith(MockKExtension::class)
class DefaultSearchUseCaseTest {
    @MockK lateinit var embedPort: EmbedPort

    @MockK lateinit var vectorStorePort: VectorStorePort

    @MockK lateinit var textIndexPort: TextIndexPort

    @MockK lateinit var aggregator: HybridSearchAggregator

    @MockK lateinit var softDedupFilter: SoftDedupFilter

    @MockK lateinit var mmrReRanker: MmrReRanker

    private lateinit var props: RetrievalSearchProperties
    private lateinit var sut: DefaultSearchUseCase
    private lateinit var defaultSpec: EmbeddingSpec

    companion object {
        @JvmStatic
        fun invalidInputs(): List<Named<Executable>> {
            fun named(
                name: String,
                block: () -> Unit,
            ) = Named.of(name, Executable { block() })
            val sut =
                DefaultSearchUseCase(
                    embedPort = mockk(),
                    vectorStorePort = mockk(),
                    textIndexPort = mockk(),
                    props = RetrievalSearchProperties(),
                    aggregator = mockk(),
                    softDedupFilter = mockk(),
                    mmrReRanker = mockk(),
                )
            return listOf(
                named("blank query") {
                    sut.search(SearchInput(queryText = "   ", topK = 3, filter = null))
                },
                named("topK <= 0") {
                    sut.search(SearchInput(queryText = "ok", topK = 0, filter = null))
                },
            )
        }
    }

    @BeforeEach
    fun setUp() {
        defaultSpec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 3, normalized = true)
        every { embedPort.spec() } returns defaultSpec
        every { embedPort.embed(any()) } returns EmbeddingVector(floatArrayOf(1f, 0f, 0f), 3, normalized = true)
        props = RetrievalSearchProperties()
        sut = buildSutWith(props)
    }

    @Test
    fun `should retrieve, aggregate, soft-dedup, mmr and return topK when all enabled`() {
        val input = SearchInput(queryText = "design system", topK = 3, filter = null)
        val collection = CollectionSpec.fromSpec(defaultSpec)
        val vectorHits = listOf(match("V1"), match("V2"))
        val bm25Hits = listOf(match("B1"), match("B2"), match("B3"))
        val fused = listOf(match("F1"), match("F2"), match("F3"), match("F4"), match("F5"))
        val afterSoft = listOf(fused[0], fused[2], fused[3], fused[4])
        val reranked = listOf(afterSoft[1], afterSoft[0], afterSoft[2])
        every { vectorStorePort.search(collection, any(), props.vector.width, null) } returns vectorHits
        every { textIndexPort.search(input.queryText, props.bm25.width, null) } returns bm25Hits
        every { aggregator.aggregate(any(), any(), any()) } returns fused
        every { softDedupFilter.filter(fused) } returns afterSoft
        every { mmrReRanker.rerank(afterSoft, input.topK) } returns reranked

        val result = sut.search(input)

        assertEquals(listOf("F3", "F1", "F4"), result.matches.map { it.documentId.value })
        verifyOrder {
            aggregator.aggregate(vectorHits, bm25Hits, any())
            softDedupFilter.filter(fused)
            mmrReRanker.rerank(afterSoft, input.topK)
        }
    }

    @Test
    fun `should work with bm25 disabled using vector only (still soft+mmr enabled)`() {
        val enabledVectorOnly = props.copy(bm25 = props.bm25.copy(enabled = false))
        val localSut = buildSutWith(enabledVectorOnly)
        val input = SearchInput(queryText = "hello", topK = 2, filter = null)
        val collection = CollectionSpec.fromSpec(defaultSpec)
        val vectorHits = listOf(match("V1"), match("V2"), match("V3"))
        val afterSoft = listOf(vectorHits[0], vectorHits[2])
        val reranked = listOf(afterSoft[1], afterSoft[0])
        every { vectorStorePort.search(collection, any(), enabledVectorOnly.vector.width, null) } returns vectorHits
        every { aggregator.aggregate(vectorHits, emptyList(), any()) } returns vectorHits
        every { softDedupFilter.filter(vectorHits) } returns afterSoft
        every { mmrReRanker.rerank(afterSoft, input.topK) } returns reranked

        val result = localSut.search(input)

        assertEquals(listOf("V3", "V1"), result.matches.map { it.documentId.value })
        verify(exactly = 0) { textIndexPort.search(any(), any(), any()) }
    }

    @Test
    fun `should work with vector disabled using bm25 only (still soft+mmr enabled)`() {
        val enabledBm25Only = props.copy(vector = props.vector.copy(enabled = false))
        val localSut = buildSutWith(enabledBm25Only)
        val input = SearchInput(queryText = "hello", topK = 2, filter = null)
        val bm25Hits = listOf(match("B1"), match("B2"), match("B3"))
        val afterSoft = listOf(bm25Hits[0], bm25Hits[2])
        val reranked = listOf(afterSoft[1], afterSoft[0])
        every { textIndexPort.search("hello", enabledBm25Only.bm25.width, null) } returns bm25Hits
        every { aggregator.aggregate(emptyList(), bm25Hits, any()) } returns bm25Hits
        every { softDedupFilter.filter(bm25Hits) } returns afterSoft
        every { mmrReRanker.rerank(afterSoft, input.topK) } returns reranked

        val result = localSut.search(input)

        assertEquals(listOf("B3", "B1"), result.matches.map { it.documentId.value })
        verify(exactly = 0) { vectorStorePort.search(any(), any(), any(), any()) }
    }

    @Test
    fun `should skip soft dedup and mmr when both disabled and return fused_take_topK`() {
        val bothDisabled =
            props.copy(
                dedup = props.dedup.copy(soft = props.dedup.soft.copy(enabled = false)),
                mmr = props.mmr.copy(enabled = false),
            )
        val localSut = buildSutWith(bothDisabled)
        val input = SearchInput(queryText = "both off", topK = 3, filter = mapOf("lang" to "pt"))
        val collection = CollectionSpec.fromSpec(defaultSpec)
        val vectorHits = listOf(match("V1"), match("V2"))
        val bm25Hits = listOf(match("B1"), match("B2"), match("B3"))
        val fused = listOf(match("F1"), match("F2"), match("F3"), match("F4"))
        every { vectorStorePort.search(collection, any(), bothDisabled.vector.width, input.filter) } returns vectorHits
        every { textIndexPort.search(input.queryText, bothDisabled.bm25.width, input.filter) } returns bm25Hits
        every { aggregator.aggregate(vectorHits, bm25Hits, any()) } returns fused

        val result = localSut.search(input)

        assertEquals(listOf("F1", "F2", "F3"), result.matches.map { it.documentId.value })
        verify(exactly = 0) { softDedupFilter.filter(any()) }
        verify(exactly = 0) { mmrReRanker.rerank(any(), any()) }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("invalidInputs")
    fun `should validate query and topK`(exec: Executable) {
        assertThrows(IllegalArgumentException::class.java, exec)
    }

    private fun match(id: String): SearchMatch =
        SearchMatch(
            documentId = DocumentId(id),
            chunk = TextChunk(text = id, metadata = mapOf("chunk_index" to "0")),
            score = 1.0,
        )

    private fun buildSutWith(props: RetrievalSearchProperties) =
        DefaultSearchUseCase(
            embedPort = embedPort,
            vectorStorePort = vectorStorePort,
            textIndexPort = textIndexPort,
            props = props,
            aggregator = aggregator,
            softDedupFilter = softDedupFilter,
            mmrReRanker = mmrReRanker,
        )
}
