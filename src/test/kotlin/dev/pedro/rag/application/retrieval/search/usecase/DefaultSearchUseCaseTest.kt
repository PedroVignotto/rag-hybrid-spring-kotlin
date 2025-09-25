package dev.pedro.rag.application.retrieval.search.usecase

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.TextIndexPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.search.dto.SearchInput
import dev.pedro.rag.application.retrieval.search.ranking.HybridSearchAggregator
import dev.pedro.rag.config.retrieval.RetrievalSearchProperties
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
            val props = RetrievalSearchProperties()
            return listOf(
                named("blank query") {
                    val useCase =
                        DefaultSearchUseCase(
                            embedPort = mockk(),
                            vectorStorePort = mockk(),
                            textIndexPort = mockk(),
                            props = props,
                            aggregator = mockk(),
                        )
                    useCase.search(SearchInput(queryText = "   ", topK = 3, filter = null))
                },
                named("topK <= 0") {
                    val useCase =
                        DefaultSearchUseCase(
                            embedPort = mockk(),
                            vectorStorePort = mockk(),
                            textIndexPort = mockk(),
                            props = props,
                            aggregator = mockk(),
                        )
                    useCase.search(SearchInput(queryText = "ok", topK = 0, filter = null))
                },
            )
        }
    }

    @BeforeEach
    fun setUp() {
        defaultSpec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 3, normalized = true)
        every { embedPort.spec() } returns defaultSpec
        every { embedPort.embed(any()) } returns EmbeddingVector(floatArrayOf(1f, 0f, 0f), 3, normalized = true)
        props = buildRetrievalSearchProperties()
        sut =
            DefaultSearchUseCase(
                embedPort = embedPort,
                vectorStorePort = vectorStorePort,
                textIndexPort = textIndexPort,
                props = props,
                aggregator = aggregator,
            )
    }

    @Test
    fun `should search both sources with their widths and aggregate respecting topK and filter`() {
        val input = SearchInput(queryText = "design system", topK = 4, filter = mapOf("lang" to "pt"))
        val collection = CollectionSpec.fromSpec(defaultSpec)
        val vectorHits = listOf(match("V1"), match("V2"))
        val bm25Hits = listOf(match("B1"), match("B2"), match("B3"))
        val fused = listOf(match("F1"), match("F2"), match("F3"), match("F4"))
        val queryVecSlot = slot<EmbeddingVector>()
        val filterVecSlot = slot<Map<String, String>?>()
        val filterBmSlot = slot<Map<String, String>?>()
        every {
            vectorStorePort.search(
                collection = collection,
                query = capture(queryVecSlot),
                topK = props.vector.width,
                filter = captureNullable(filterVecSlot),
            )
        } returns vectorHits
        every {
            textIndexPort.search(
                query = input.queryText,
                width = props.bm25.width,
                filter = captureNullable(filterBmSlot),
            )
        } returns bm25Hits
        every { aggregator.aggregate(vectorHits = vectorHits, bm25Hits = bm25Hits, k = input.topK) } returns fused

        val result = sut.search(input)

        assertEquals(fused, result.matches)
        verify(exactly = 1) { embedPort.spec() }
        verify(exactly = 1) { embedPort.embed("design system") }
        verify(exactly = 1) { vectorStorePort.search(collection, any(), props.vector.width, any()) }
        verify(exactly = 1) { textIndexPort.search("design system", props.bm25.width, any()) }
        verify(exactly = 1) { aggregator.aggregate(vectorHits, bm25Hits, 4) }
        assertEquals(3, queryVecSlot.captured.dim)
        assertEquals(true, queryVecSlot.captured.normalized)
        assertEquals(input.filter, filterVecSlot.captured)
        assertEquals(input.filter, filterBmSlot.captured)
        confirmVerified(vectorStorePort, textIndexPort, aggregator)
    }

    @Test
    fun `should work when bm25 is disabled using only vector results`() {
        val disabledBm25 = props.copy(bm25 = props.bm25.copy(enabled = false))
        val localSut = DefaultSearchUseCase(embedPort, vectorStorePort, textIndexPort, disabledBm25, aggregator)
        val input = SearchInput(queryText = "hello", topK = 2, filter = null)
        val collection = CollectionSpec.fromSpec(defaultSpec)
        val queryVec = embedPort.embed(input.queryText)
        val vectorHits = listOf(match("V"))
        every { vectorStorePort.search(collection, queryVec, disabledBm25.vector.width, null) } returns vectorHits
        every { aggregator.aggregate(vectorHits, emptyList(), k = 2) } returns vectorHits

        val result = localSut.search(input)

        assertEquals(1, result.matches.size)
        verify(exactly = 0) { textIndexPort.search(any(), any(), any()) }
    }

    @Test
    fun `should work when vector is disabled using only bm25 results`() {
        val disabledVector = props.copy(vector = props.vector.copy(enabled = false))
        val localSut = DefaultSearchUseCase(embedPort, vectorStorePort, textIndexPort, disabledVector, aggregator)
        val input = SearchInput(queryText = "hello", topK = 2, filter = null)
        val bm25Hits = listOf(match("B"))
        every { textIndexPort.search(query = "hello", width = disabledVector.bm25.width, filter = null) } returns bm25Hits
        every { aggregator.aggregate(emptyList(), bm25Hits, k = 2) } returns bm25Hits

        val result = localSut.search(input)

        assertEquals(1, result.matches.size)
        verify(exactly = 0) { vectorStorePort.search(any(), any(), any(), any()) }
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

    private fun buildRetrievalSearchProperties() =
        RetrievalSearchProperties(
            k = 10,
            vector = RetrievalSearchProperties.Vector(enabled = true, width = 3),
            bm25 =
                RetrievalSearchProperties.Bm25(
                    enabled = true,
                    width = 5,
                    termFrequencySaturation = 1.2,
                    lengthNormalization = 0.75,
                    stopWordsEnabled = false,
                    stopWords = emptySet(),
                ),
            fusion = RetrievalSearchProperties.Fusion(alpha = 0.4),
            dedup = RetrievalSearchProperties.Dedup(),
            mmr = RetrievalSearchProperties.Mmr(),
        )
}
