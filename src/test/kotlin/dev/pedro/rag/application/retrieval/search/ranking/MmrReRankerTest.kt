package dev.pedro.rag.application.retrieval.search.ranking

import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MmrReRankerTest {
    @Test
    fun `should honor lambda extremes (lambda 1_0 picks relevance, lambda 0_0 picks diversity after first)`() {
        val hits =
            listOf(
                hit(doc = "D", idx = 0, score = 0.90, text = "alpha beta"),
                hit(doc = "D", idx = 1, score = 0.80, text = "alpha beta"),
                hit(doc = "D", idx = 2, score = 0.40, text = "x y z"),
            )

        val onlyRelevance = MmrReRanker(lambda = 1.0).rerank(hits, k = 2)
        val onlyDiversity = MmrReRanker(lambda = 0.0).rerank(hits, k = 2)

        assertEquals(listOf(0, 1), onlyRelevance.map { idxOf(it) })
        assertEquals(listOf(0, 2), onlyDiversity.map { idxOf(it) })
    }

    @Test
    fun `should promote diversity with intermediate lambda`() {
        val hits =
            listOf(
                hit("D", 0, 1.00, "alpha beta"),
                hit("D", 1, 0.95, "alpha beta"),
                hit("D", 2, 0.70, "x y"),
            )
        val sut = MmrReRanker(lambda = 0.30)

        val result = sut.rerank(hits, k = 2)

        assertEquals(listOf(0, 2), result.map { idxOf(it) })
    }

    @Test
    fun `should respect k less than list size`() {
        val hits =
            listOf(
                hit("A", 0, 0.9, "foo bar"),
                hit("B", 0, 0.8, "baz qux"),
                hit("C", 0, 0.7, "lorem ipsum"),
            )
        val sut = MmrReRanker(lambda = 0.5)

        val result = sut.rerank(hits, k = 2)

        assertEquals(2, result.size)
    }

    @Test
    fun `should tie-break by documentId when mmr and score are equal`() {
        val hits =
            listOf(
                hit("A", 5, 0.8, "foo"),
                hit("B", 1, 0.8, "foo"),
            )
        val sut = MmrReRanker(lambda = 1.0)

        val result = sut.rerank(hits, k = 1)

        assertEquals("A", result.first().documentId.value)
    }

    @Test
    fun `should tie-break by chunk_index when mmr, score and documentId are equal`() {
        val hits =
            listOf(
                hit("D", 2, 0.75, "same text"),
                hit("D", 0, 0.75, "same text"),
            )
        val sut = MmrReRanker(lambda = 1.0)

        val result = sut.rerank(hits, k = 1)

        assertEquals(0, idxOf(result.first()))
    }

    @Test
    fun `should treat missing chunk_index as larger than any explicit index`() {
        val withIndex =
            SearchMatch(
                documentId = DocumentId("D"),
                chunk = TextChunk(text = "foo", metadata = mapOf("chunk_index" to "0")),
                score = 0.5,
            )
        val noIndex =
            SearchMatch(
                documentId = DocumentId("D"),
                chunk = TextChunk(text = "foo", metadata = emptyMap()),
                score = 0.5,
            )
        val sut = MmrReRanker(lambda = 1.0)

        val result = sut.rerank(listOf(noIndex, withIndex), k = 2)

        assertEquals(listOf("0", null), result.map { it.chunk.metadata["chunk_index"] })
    }

    @Test
    fun `should handle blank or punctuation-only text without crashing and fallback to scores`() {
        val hits =
            listOf(
                hit("A", 0, 0.6, "   ,.;  "),
                hit("B", 0, 0.7, "alpha"),
            )
        val sut = MmrReRanker(lambda = 0.2)

        val result = sut.rerank(hits, k = 2)

        assertEquals(listOf("B", "A"), result.map { it.documentId.value })
    }

    @Test
    fun `should return empty when hits list is empty`() {
        val sut = MmrReRanker(lambda = 0.5)

        val result = sut.rerank(emptyList(), k = 5)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return empty when k is non positive`() {
        val sut = MmrReRanker(lambda = 0.5)
        val oneHit = listOf(hit("A", 0, 0.5, "x"))

        val result = sut.rerank(oneHit, k = 0)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should prefer higher score on MMR tie (lambda 0_0 makes first-step MMR tie)`() {
        val sut = MmrReRanker(lambda = 0.0)
        val hits =
            listOf(
                hit("D", 0, 0.60, "alpha"),
                hit("D", 1, 0.90, "beta"),
            )

        val result = sut.rerank(hits, k = 1)

        assertEquals(1, idxOf(result.first()))
    }

    @Test
    fun `should not replace incumbent when chunk_index is larger on full tie`() {
        val hits =
            listOf(
                hit("D", 0, 0.75, "same text"),
                hit("D", 2, 0.75, "same text"),
            )
        val sut = MmrReRanker(lambda = 1.0)

        val result = sut.rerank(hits, k = 1)

        assertEquals(0, idxOf(result.first()))
    }

    @Test
    fun `should treat tiny MMR difference within EPS as tie and fall back to docId`() {
        val a = hit("A", 0, 0.8000000000000, "foo")
        val b = hit("B", 0, 0.8000000000005, "foo")
        val sut = MmrReRanker(lambda = 1.0)

        val result = sut.rerank(listOf(a, b), k = 1)

        assertEquals("A", result.first().documentId.value)
    }

    @Test
    fun `should validate lambda bounds`() {
        assertThrows<IllegalArgumentException> { MmrReRanker(-0.01) }
        assertThrows<IllegalArgumentException> { MmrReRanker(1.01) }
    }

    @Test
    fun `should not replace incumbent when candidate has lower score on mmr tie`() {
        val sut = MmrReRanker(lambda = 0.0)
        val hits =
            listOf(
                hit("D", 0, 0.90, "alpha"),
                hit("D", 1, 0.10, "beta"),
            )

        val result = sut.rerank(hits, k = 1)

        assertEquals(0, idxOf(result.first()))
    }

    @Test
    fun `should replace incumbent when candidate docId is lexicographically smaller on full tie`() {
        val sut = MmrReRanker(lambda = 1.0)
        val hits =
            listOf(
                hit("Z", 0, 0.80, "same text"),
                hit("A", 0, 0.80, "same text"),
            )

        val result = sut.rerank(hits, k = 1)

        assertEquals("A", result.first().documentId.value)
    }

    private fun hit(
        doc: String,
        idx: Int,
        score: Double,
        text: String,
    ): SearchMatch =
        SearchMatch(
            documentId = DocumentId(doc),
            chunk = TextChunk(text = text, metadata = mapOf("chunk_index" to idx.toString())),
            score = score,
        )

    private fun idxOf(m: SearchMatch): Int = m.chunk.metadata["chunk_index"]!!.toInt()
}
