package dev.pedro.rag.application.retrieval.delete.usecase

import dev.pedro.rag.application.retrieval.ports.TextIndexPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.DocumentId
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class DefaultDeleteUseCaseTest(
    @param:MockK private val vectorStorePort: VectorStorePort,
    @param:MockK private val collectionSpec: CollectionSpec,
    @param:MockK(relaxed = true) private val textIndexPort: TextIndexPort,
) {
    @InjectMockKs
    private lateinit var sut: DefaultDeleteUseCase

    @Test
    fun `handle should return deleted count and delegate to vector and lexical index`() {
        val docId = DocumentId("doc-1")
        every { vectorStorePort.deleteByDocumentId(collectionSpec, docId) } returns 2

        val out = sut.handle(docId)

        assertEquals(2, out.deleted)
        verify(exactly = 1) { vectorStorePort.deleteByDocumentId(collectionSpec, docId) }
        verify(exactly = 1) { textIndexPort.delete(docId) }
        confirmVerified(vectorStorePort, textIndexPort)
    }

    @Test
    fun `handle should return 0 when nothing is deleted and still call lexical delete`() {
        val docId = DocumentId("not-found")
        every { vectorStorePort.deleteByDocumentId(collectionSpec, docId) } returns 0

        val out = sut.handle(docId)

        assertEquals(0, out.deleted)
        verify(exactly = 1) { vectorStorePort.deleteByDocumentId(collectionSpec, docId) }
        verify(exactly = 1) { textIndexPort.delete(docId) }
        confirmVerified(vectorStorePort, textIndexPort)
    }
}
