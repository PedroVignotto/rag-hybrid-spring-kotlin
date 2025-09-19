package dev.pedro.rag.infra.retrieval.chunker

import dev.pedro.rag.domain.retrieval.TextChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SimpleChunkerTest {
    private val sut = SimpleChunker()

    @Test
    fun `should return empty list when text is blank`() {
        assertTrue(sut.split("", chunkSize = 3, overlap = 1).isEmpty())
        assertTrue(sut.split("   ", chunkSize = 3, overlap = 1).isEmpty())
    }

    @Test
    fun `should split text respecting chunk size and overlap in typical case`() {
        val text = "ABCDE"
        val chunks: List<TextChunk> = sut.split(text, chunkSize = 3, overlap = 1)

        val chunkTexts = chunks.map { it.text }
        assertEquals(listOf("ABC", "CDE", "E"), chunkTexts)
    }

    @Test
    fun `should include metadata chunk_index and chunk_total on each chunk`() {
        val text = "ABCDE"
        val chunks = sut.split(text, chunkSize = 3, overlap = 1)

        val total = chunks.size
        chunks.forEachIndexed { index, c ->
            assertEquals(index.toString(), c.metadata[SimpleChunker.META_CHUNK_INDEX])
            assertEquals(total.toString(), c.metadata[SimpleChunker.META_CHUNK_TOTAL])
        }
    }

    @Test
    fun `should throw when chunkSize is less than minimum`() {
        val exception =
            assertThrows<IllegalStateException> {
                sut.split(text = "abc", chunkSize = 0, overlap = 0)
            }
        assertTrue(exception.message!!.contains("chunkSize"))
    }

    @Test
    fun `should throw when overlap is negative`() {
        val exception =
            assertThrows<IllegalStateException> {
                sut.split(text = "abc", chunkSize = 3, overlap = -1)
            }
        assertTrue(exception.message!!.contains("overlap"))
    }

    @Test
    fun `should throw when overlap is not less than chunkSize`() {
        val exception =
            assertThrows<IllegalStateException> {
                sut.split(text = "abcdef", chunkSize = 3, overlap = 3)
            }
        assertTrue(exception.message!!.contains("overlap"))
    }

    @Test
    fun `should return single chunk when text is shorter than chunkSize`() {
        val chunks = sut.split(text = "AB", chunkSize = 5, overlap = 1)

        assertEquals(1, chunks.size)
        assertEquals("AB", chunks.first().text)
        assertEquals("0", chunks.first().metadata[SimpleChunker.META_CHUNK_INDEX])
        assertEquals("1", chunks.first().metadata[SimpleChunker.META_CHUNK_TOTAL])
    }

    @Test
    fun `should produce sliding window when overlap equals chunkSize minus one`() {
        val text = "ABCDE"
        val chunkSize = 3
        val overlap = 2

        val chunks = sut.split(text, chunkSize, overlap)

        assertEquals(listOf("ABC", "BCD", "CDE", "DE", "E"), chunks.map { it.text })
        assertTrue(chunks.all { it.metadata[SimpleChunker.META_CHUNK_TOTAL] == chunks.size.toString() })
    }
}
