package dev.pedro.rag.application.retrieval.usecase

import dev.pedro.rag.application.retrieval.ports.Chunker
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.usecase.ingest.IngestCommand
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.TextChunk
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@ExtendWith(MockKExtension::class)
class IngestUseCaseTest(
    @param:MockK private val chunker: Chunker,
    @param:MockK private val embedPort: EmbedPort,
    @param:MockK private val vectorStorePort: VectorStorePort,
) {
    @InjectMockKs
    private lateinit var sut: IngestUseCase
    private lateinit var defaultSpec: EmbeddingSpec

    companion object {
        @JvmStatic
        fun invalidCommands(): List<Named<Executable>> {
            fun named(
                name: String,
                block: () -> Unit,
            ) = Named.of(name, Executable { block() })
            val base =
                IngestCommand(
                    documentId = DocumentId("doc-invalid"),
                    text = "abc",
                    baseMetadata = emptyMap(),
                    chunkSize = 5,
                    overlap = 2,
                )
            return listOf(
                named("blank text") {
                    IngestUseCase(mockk(), mockk(), mockk()).ingest(base.copy(text = "   "))
                },
                named("chunkSize <= 0") {
                    IngestUseCase(mockk(), mockk(), mockk()).ingest(base.copy(chunkSize = 0))
                },
                named("overlap < 0") {
                    IngestUseCase(mockk(), mockk(), mockk()).ingest(base.copy(overlap = -1))
                },
                named("overlap >= chunkSize") {
                    IngestUseCase(mockk(), mockk(), mockk()).ingest(base.copy(chunkSize = 5, overlap = 5))
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
    fun `should chunk, embed and upsert with chunk metadata precedence`() {
        val command =
            validCommand(
                documentId = "doc-1",
                text = "ABCDEFGHIJ",
                baseMetadata = mapOf("location" to "hq", "chunk_index" to "999"),
                chunkSize = 5,
                overlap = 2,
            )
        val producedChunks =
            listOf(
                chunk("ABCDE", idx = 0, total = 4),
                chunk("DEFGH", idx = 1, total = 4),
                chunk("GHIJ", idx = 2, total = 4),
                chunk("J", idx = 3, total = 4),
            )
        every { chunker.split(command.text, command.chunkSize, command.overlap) } returns producedChunks
        every { embedPort.embedAll(any()) } answers {
            firstArg<List<String>>().map {
                EmbeddingVector(values = floatArrayOf(1f, 0f, 0f), dim = defaultSpec.dim, normalized = true)
            }
        }
        val collectionSlot: CapturingSlot<CollectionSpec> = slot()
        val documentIdSlot: CapturingSlot<DocumentId> = slot()
        val itemsSlot: CapturingSlot<List<Pair<TextChunk, EmbeddingVector>>> = slot()
        every { vectorStorePort.upsert(capture(collectionSlot), capture(documentIdSlot), capture(itemsSlot)) } just Runs

        val result = sut.ingest(command)

        assertEquals("doc-1", result.documentId.value)
        assertEquals(4, result.chunksIngested)
        assertEquals(CollectionSpec("fake", "fake-v1", 3), collectionSlot.captured)
        assertEquals("doc-1", documentIdSlot.captured.value)
        val first = itemsSlot.captured.first().first
        assertEquals("0", first.metadata["chunk_index"])
        assertEquals("4", first.metadata["chunk_total"])
        assertEquals("hq", first.metadata["location"])
        assertTrue(itemsSlot.captured.all { it.second.dim == 3 })
        verify(exactly = 1) { chunker.split(command.text, command.chunkSize, command.overlap) }
        verify(exactly = 1) { embedPort.embedAll(listOf("ABCDE", "DEFGH", "GHIJ", "J")) }
        verify(exactly = 1) { vectorStorePort.upsert(any(), any(), any()) }
    }

    @Test
    fun `should return zero when chunker yields no chunks and must not call embed or upsert`() {
        val command = validCommand(text = "abc", chunkSize = 10, overlap = 0)
        every { chunker.split(command.text, command.chunkSize, command.overlap) } returns emptyList()

        val result = sut.ingest(command)

        assertEquals("doc-valid", result.documentId.value)
        assertEquals(0, result.chunksIngested)
        verify(exactly = 1) { embedPort.spec() }
        verify(exactly = 0) { embedPort.embedAll(any()) }
        verify(exactly = 0) { vectorStorePort.upsert(any(), any(), any()) }
    }

    @Test
    fun `should fail when any embedding vector has wrong dimension`() {
        val command = validCommand(text = "123456", chunkSize = 6, overlap = 0)
        every { chunker.split(any(), any(), any()) } returns listOf(chunk("123456", 0, 1))
        every { embedPort.embedAll(any()) } returns
            listOf(
                EmbeddingVector(values = floatArrayOf(1f, 2f), dim = 2, normalized = true),
            )

        assertThrows(IllegalArgumentException::class.java) { sut.ingest(command) }
        verify(exactly = 0) { vectorStorePort.upsert(any(), any(), any()) }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("invalidCommands")
    fun `should validate command parameters`(exec: Executable) {
        assertThrows(IllegalArgumentException::class.java, exec)
    }

    private fun validCommand(
        documentId: String = "doc-valid",
        text: String = "ABCDEFGHIJ",
        baseMetadata: Map<String, String> = emptyMap(),
        chunkSize: Int = 5,
        overlap: Int = 2,
    ) = IngestCommand(
        documentId = DocumentId(documentId),
        text = text,
        baseMetadata = baseMetadata,
        chunkSize = chunkSize,
        overlap = overlap,
    )

    private fun chunk(
        text: String,
        idx: Int,
        total: Int,
    ) = TextChunk(text = text, metadata = mapOf("chunk_index" to "$idx", "chunk_total" to "$total"))
}
