package dev.pedro.rag.infra.retrieval.textindex.bm25

import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.TextChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InMemoryTextIndexStoreTest {
    private lateinit var sut: InMemoryTextIndexStore

    @BeforeEach
    fun setUp() {
        sut = InMemoryTextIndexStore()
    }

    @Test
    fun `should return empty when query is blank`() {
        sut.index(DocumentId("d1"), listOf(chunk("hello world", 0)))

        val result = sut.search("", width = 10)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return empty when width is invalid`() {
        sut.index(DocumentId("d1"), listOf(chunk("hello world", 0)))

        val result = sut.search("hello", width = 0)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should rank higher chunks with higher term frequency`() {
        sut.index(
            DocumentId("doc"),
            listOf(
                chunk("design design design", 0),
                chunk("design", 1),
            ),
        )

        val result = sut.search("design", width = 2)

        assertEquals(2, result.size)
        assertEquals(0, result[0].chunk.metadata["chunk_index"]!!.toInt())
        assertEquals(1, result[1].chunk.metadata["chunk_index"]!!.toInt())
    }

    @Test
    fun `should tokenize pt-BR ignoring case and accents`() {
        sut.index(DocumentId("pt"), listOf(chunk("Coração, DESIGN!", 0)))

        val result = sut.search("coracao design", width = 5)

        assertTrue(result.isNotEmpty())
        assertEquals("Coração, DESIGN!", result.first().chunk.text)
    }

    @Test
    fun `should tokenize english ignoring case and punctuation`() {
        sut.index(DocumentId("en"), listOf(chunk("Hello, WORLD!! Welcome-to_search.", 0)))

        val result = sut.search("hello world welcome search", width = 5)

        assertTrue(result.isNotEmpty())
        assertEquals("Hello, WORLD!! Welcome-to_search.", result.first().chunk.text)
    }

    @Test
    fun `should apply stopWords when enabled (en)`() {
        val sut =
            InMemoryTextIndexStore(
                stopWordsEnabled = true,
                stopWords = setOf("the", "and", "of"),
            )
        sut.index(DocumentId("s-en-1"), listOf(chunk("the the and and of of", 0)))

        val result = sut.search("the and of", width = 5)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should ignore stopWords when disabled (en)`() {
        val storeNoStop =
            InMemoryTextIndexStore(
                stopWordsEnabled = false,
                stopWords = setOf("the", "and", "of"),
            )
        storeNoStop.index(DocumentId("s-en-2"), listOf(chunk("the the and and of of", 0)))

        val result = storeNoStop.search("the and of", width = 5)

        assertTrue(result.isNotEmpty())
        assertEquals("0", result.first().chunk.metadata["chunk_index"])
    }

    @Test
    fun `should delete by document id`() {
        sut.index(DocumentId("d1"), listOf(chunk("alpha beta", 0)))
        sut.index(DocumentId("d2"), listOf(chunk("alpha beta", 0)))

        val removed = sut.delete(DocumentId("d1"))
        val result = sut.search("alpha", width = 10)

        assertTrue(removed > 0)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.documentId == DocumentId("d2") })
    }

    @Test
    fun `should be idempotent on reindex same document and chunk index`() {
        val doc = DocumentId("reindex")
        sut.index(doc, listOf(chunk("alpha", 0)))

        val before = sut.search("alpha", width = 5)
        sut.index(doc, listOf(chunk("beta", 0)))
        val afterAlpha = sut.search("alpha", width = 5)
        val afterBeta = sut.search("beta", width = 5)

        assertTrue(before.isNotEmpty())
        assertTrue(afterAlpha.isEmpty())
        assertTrue(afterBeta.isNotEmpty())
    }

    @Test
    fun `should respect width limit`() {
        sut.index(
            DocumentId("doc-width"),
            listOf(
                chunk("design", 0),
                chunk("design", 1),
                chunk("design", 2),
                chunk("design", 3),
            ),
        )

        val result = sut.search("design", width = 2)

        assertEquals(2, result.size)
    }

    @Test
    fun `should report size after index and delete`() {
        assertEquals(0, sut.size())
        sut.index(DocumentId("d1"), listOf(chunk("a b c", 0), chunk("d e f", 1)))
        sut.index(DocumentId("d2"), listOf(chunk("x y", 0)))

        val sizeAfterIndex = sut.size()
        sut.delete(DocumentId("d1"))
        val sizeAfterDelete = sut.size()

        assertEquals(3, sizeAfterIndex)
        assertEquals(1, sizeAfterDelete)
    }

    private fun chunk(
        text: String,
        index: Int,
    ): TextChunk = TextChunk(text = text, metadata = mapOf("chunk_index" to index.toString()))
}
