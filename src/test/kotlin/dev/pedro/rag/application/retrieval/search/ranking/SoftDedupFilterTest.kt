package dev.pedro.rag.application.retrieval.search.ranking

import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SoftDedupFilterTest {
    @Test
    fun `should keep first and drop near duplicates within same document`() {
        val sut = SoftDedupFilter(overlapThreshold = 0.75)
        val hits =
            listOf(
                hit("doc1", 0, "alpha beta gamma"),
                hit("doc1", 1, "alpha! bêta? gamma"),
                hit("doc1", 2, "alpha beta"),
            )

        val result = sut.filter(hits)

        assertEquals(listOf(0, 2), result.map { it.chunk.metadata["chunk_index"]!!.toInt() })
        assertEquals(2, result.size)
    }

    @Test
    fun `should not deduplicate across different documents`() {
        val sut = SoftDedupFilter(overlapThreshold = 0.9)
        val hits =
            listOf(
                hit("A", 0, "alpha beta gamma"),
                hit("B", 0, "alpha beta gamma"),
            )

        val result = sut.filter(hits)

        assertEquals(2, result.size)
        assertEquals(listOf("A", "B"), result.map { it.documentId.value })
    }

    @Test
    fun `should preserve original order of remaining hits`() {
        val sut = SoftDedupFilter(overlapThreshold = 0.75)
        val hits =
            listOf(
                hit("doc", 0, "design system overview"),
                hit("doc", 1, "overview of design system"),
                hit("doc", 2, "design tokens and components"),
                hit("doc", 3, "components and tokens for design"),
            )

        val result = sut.filter(hits)

        assertEquals(listOf(0, 2), result.map { it.chunk.metadata["chunk_index"]!!.toInt() })
    }

    @Test
    fun `should handle empty input`() {
        val sut = SoftDedupFilter(overlapThreshold = 0.75)

        val result = sut.filter(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should treat blank or punctuation-only chunk as unique`() {
        val sut = SoftDedupFilter(overlapThreshold = 0.75)
        val hits =
            listOf(
                hit("D", 0, "alpha beta"),
                hit("D", 1, "   ,.;   "),
            )

        val result = sut.filter(hits)

        assertEquals(listOf(0, 1), result.map { it.chunk.metadata["chunk_index"]!!.toInt() })
    }

    @Test
    fun `should keep when first chunk is blank and second has tokens (jaccard zero)`() {
        val sut = SoftDedupFilter(overlapThreshold = 0.75)
        val hits =
            listOf(
                hit("D", 0, "   "),
                hit("D", 1, "alpha"),
            )

        val result = sut.filter(hits)

        assertEquals(listOf(0, 1), result.map { it.chunk.metadata["chunk_index"]!!.toInt() })
    }

    @ParameterizedTest(name = "invalid threshold → {0}")
    @ValueSource(doubles = [-0.01, 1.01])
    fun `should reject invalid thresholds`(value: Double) {
        assertThrows(IllegalArgumentException::class.java) { SoftDedupFilter(overlapThreshold = value) }
    }

    @ParameterizedTest(name = "boundary threshold ok → {0}")
    @ValueSource(doubles = [0.0, 1.0])
    fun `should accept boundary thresholds`(value: Double) {
        assertDoesNotThrow { SoftDedupFilter(overlapThreshold = value) }
    }

    private fun hit(
        doc: String,
        idx: Int,
        text: String,
    ): SearchMatch =
        SearchMatch(
            documentId = DocumentId(doc),
            chunk = TextChunk(text = text, metadata = mapOf("chunk_index" to idx.toString())),
            score = 1.0,
        )
}
