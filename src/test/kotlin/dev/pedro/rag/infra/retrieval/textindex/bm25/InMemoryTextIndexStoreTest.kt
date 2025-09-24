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
        val sut =
            InMemoryTextIndexStore(
                stopWordsEnabled = false,
                stopWords = setOf("the", "and", "of"),
            )
        sut.index(DocumentId("s-en-2"), listOf(chunk("the the and and of of", 0)))

        val result = sut.search("the and of", width = 5)

        assertTrue(result.isNotEmpty())
        assertEquals("0", result.first().chunk.metadata["chunk_index"])
    }

    private fun chunk(
        text: String,
        index: Int,
    ): TextChunk = TextChunk(text = text, metadata = mapOf("chunk_index" to index.toString()))
}
