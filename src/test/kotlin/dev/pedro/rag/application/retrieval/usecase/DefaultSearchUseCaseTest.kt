package dev.pedro.rag.application.retrieval.usecase

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.search.dto.SearchInput
import dev.pedro.rag.application.retrieval.search.usecase.DefaultSearchUseCase
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
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
class DefaultSearchUseCaseTest(
    @param:MockK private val embedPort: EmbedPort,
    @param:MockK private val vectorStorePort: VectorStorePort,
) {
    @InjectMockKs
    private lateinit var sut: DefaultSearchUseCase
    private lateinit var defaultSpec: EmbeddingSpec

    companion object {
        @JvmStatic
        fun invalidInputs(): List<Named<Executable>> {
            fun named(
                name: String,
                block: () -> Unit,
            ) = Named.of(name, Executable { block() })

            return listOf(
                named("blank query") {
                    DefaultSearchUseCase(
                        embedPort = mockk(),
                        vectorStorePort = mockk(),
                    ).search(SearchInput(queryText = "   ", topK = 3, filter = null))
                },
                named("topK <= 0") {
                    DefaultSearchUseCase(
                        embedPort = mockk(),
                        vectorStorePort = mockk(),
                    ).search(SearchInput(queryText = "ok", topK = 0, filter = null))
                },
            )
        }
    }

    @BeforeEach
    fun stubSpec() {
        defaultSpec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 3, normalized = true)
        every { embedPort.spec() } returns defaultSpec
    }

    @Test
    fun `should embed query and search in namespaced collection`() {
        val input = SearchInput(queryText = "what is in x-bacon?", topK = 3, filter = null)
        val queryVector = EmbeddingVector(values = floatArrayOf(1f, 0f, 0f), dim = 3, normalized = true)
        every { embedPort.embed(input.queryText) } returns queryVector
        val collectionSlot: CapturingSlot<CollectionSpec> = slot()
        val returned =
            listOf(
                SearchMatch(
                    documentId = DocumentId("doc-1"),
                    chunk = TextChunk(text = "X-Bacon: bun, 150g beef, bacon, cheese, mayo", metadata = mapOf("store" to "hq")),
                    score = 0.92,
                ),
            )
        every {
            vectorStorePort.search(
                collection = capture(collectionSlot),
                query = queryVector,
                topK = input.topK,
                filter = input.filter,
            )
        } returns returned

        val out = sut.search(input)

        assertEquals(returned, out.matches)
        assertEquals(CollectionSpec("fake", "fake-v1", 3), collectionSlot.captured)
        verify(exactly = 1) { embedPort.embed(input.queryText) }
        verify(exactly = 1) { vectorStorePort.search(collectionSlot.captured, queryVector, input.topK, input.filter) }
    }

    @Test
    fun `should forward filter and topK to vector store`() {
        val input =
            SearchInput(
                queryText = "menu items with bacon",
                topK = 5,
                filter = mapOf("store" to "hq", "type" to "menu"),
            )
        val queryVector = EmbeddingVector(values = floatArrayOf(0f, 1f, 0f), dim = 3, normalized = true)
        every { embedPort.embed(input.queryText) } returns queryVector
        every { vectorStorePort.search(any(), queryVector, input.topK, input.filter) } returns emptyList()

        val out = sut.search(input)

        assertEquals(0, out.matches.size)
        verify(exactly = 1) { vectorStorePort.search(any(), queryVector, input.topK, input.filter) }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("invalidInputs")
    fun `should validate query and topK`(exec: Executable) {
        assertThrows(IllegalArgumentException::class.java, exec)
    }
}
