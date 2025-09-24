package dev.pedro.rag.application.retrieval.ports

import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.TextChunk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class TextIndexPortContractTest {
    protected lateinit var sut: TextIndexPort

    protected abstract fun newSut(): TextIndexPort

    companion object {
        @JvmStatic
        fun invalidInputs(): List<Named<Pair<String, Int>>> =
            listOf(
                Named.of("blank query", "" to 10),
                Named.of("non-positive width", "hello" to 0),
            )
    }

    @BeforeEach
    fun setUp() {
        sut = newSut()
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("invalidInputs")
    fun `should return empty on invalid input`(input: Pair<String, Int>) {
        sut.index(DocumentId("d1"), listOf(chunk("hello world", 0)))

        val result = sut.search(query = input.first, width = input.second)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should index search and respect width`() {
        val doc = DocumentId("doc")
        sut.index(
            doc,
            listOf(
                chunk("foo", 0),
                chunk("foo", 1),
                chunk("foo", 2),
            ),
        )

        val result = sut.search(query = "foo", width = 2)

        assertEquals(2, result.size)
        assertTrue(result.all { it.documentId == doc })
    }

    @Test
    fun `should honor metadata filter before scoring`() {
        val doc = DocumentId("doc-filter")
        sut.index(
            doc,
            listOf(
                chunk("design ux", 0, mapOf("lang" to "en")),
                chunk("design produto", 1, mapOf("lang" to "pt")),
            ),
        )

        val enOnly = sut.search(query = "design", width = 10, filter = mapOf("lang" to "en"))
        val ptOnly = sut.search(query = "design", width = 10, filter = mapOf("lang" to "pt"))
        val none = sut.search(query = "design", width = 10, filter = mapOf("lang" to "es"))

        assertEquals(listOf("0"), enOnly.map { it.chunk.metadata["chunk_index"] })
        assertEquals(listOf("1"), ptOnly.map { it.chunk.metadata["chunk_index"] })
        assertTrue(none.isEmpty())
    }

    @Test
    fun `should delete by document id`() {
        val d1 = DocumentId("d1")
        val d2 = DocumentId("d2")
        sut.index(d1, listOf(chunk("alpha beta", 0)))
        sut.index(d2, listOf(chunk("alpha beta", 0)))

        val removed = sut.delete(d1)
        val result = sut.search(query = "alpha", width = 10)

        assertTrue(removed > 0)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.documentId == d2 })
    }

    @Test
    fun `should be idempotent on reindex same document and chunk index`() {
        val doc = DocumentId("reindex")
        sut.index(doc, listOf(chunk("alpha", 0)))

        val before = sut.search(query = "alpha", width = 5)
        sut.index(doc, listOf(chunk("beta", 0)))
        val afterAlpha = sut.search(query = "alpha", width = 5)
        val afterBeta = sut.search(query = "beta", width = 5)

        assertTrue(before.isNotEmpty())
        assertTrue(afterAlpha.isEmpty())
        assertTrue(afterBeta.isNotEmpty())
    }

    @Test
    fun `should order by documentId then chunkIndex when scores are equal`() {
        val d1 = DocumentId("a")
        val d2 = DocumentId("b")
        sut.index(d1, listOf(chunk("foo", 2), chunk("foo", 0)))
        sut.index(d2, listOf(chunk("foo", 1)))

        val result = sut.search(query = "foo", width = 10)

        val ordered = result.map { it.documentId.value to it.chunk.metadata["chunk_index"] }
        assertEquals(listOf("a" to "0", "a" to "2", "b" to "1"), ordered)
    }

    @Test
    fun `should report size after index and delete`() {
        assertEquals(0, sut.size())
        val d1 = DocumentId("d1")
        val d2 = DocumentId("d2")

        sut.index(d1, listOf(chunk("a b c", 0), chunk("d e f", 1)))
        sut.index(d2, listOf(chunk("x y", 0)))
        val sizeAfterIndex = sut.size()
        sut.delete(d1)
        val sizeAfterDelete = sut.size()

        assertEquals(3, sizeAfterIndex)
        assertEquals(1, sizeAfterDelete)
    }

    protected fun chunk(
        text: String,
        index: Int,
        extra: Map<String, String> = emptyMap(),
    ): TextChunk = TextChunk(text = text, metadata = mapOf("chunk_index" to index.toString()) + extra)
}
