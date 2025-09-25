package dev.pedro.rag.application.retrieval.search.ranking

import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridSearchAggregatorTest {
    @Test
    fun `should return empty when k is non positive or both sources are empty`() {
        val sut = HybridSearchAggregator(alpha = 0.5)

        assertTrue(sut.aggregate(vectorHits = emptyList(), bm25Hits = emptyList(), k = 0).isEmpty())
        assertTrue(sut.aggregate(vectorHits = emptyList(), bm25Hits = emptyList(), k = 5).isEmpty())
    }

    @Test
    fun `should aggregate when one source is empty and keep order by the other`() {
        val sut = HybridSearchAggregator(alpha = 0.5)
        val vector = emptyList<SearchMatch>()
        val bm25 =
            listOf(
                hit(doc = "A", idx = 0, score = 10.0),
                hit(doc = "B", idx = 0, score = 5.0),
            )

        val result = sut.aggregate(vectorHits = vector, bm25Hits = bm25, k = 2)

        assertEquals(listOf("A", "B"), result.map { it.documentId.value })
    }

    @Test
    fun `should fuse scores with alpha and respect topK`() {
        val sut = HybridSearchAggregator(alpha = 0.3)
        val vector =
            listOf(
                hit("A", 0, 0.90, text = "vec-A0"),
                hit("A", 1, 0.40, text = "vec-A1"),
                hit("B", 0, 0.70, text = "vec-B0"),
            )
        val bm25 =
            listOf(
                hit("A", 0, 2.0, text = "bm25-A0"),
                hit("B", 0, 5.0, text = "bm25-B0"),
                hit("C", 0, 1.0, text = "bm25-C0"),
            )

        val result = sut.aggregate(vectorHits = vector, bm25Hits = bm25, k = 2)

        val ordered = result.map { it.documentId.value to it.chunk.metadata["chunk_index"] }
        assertEquals(listOf("B" to "0", "A" to "0"), ordered)
    }

    @Test
    fun `should prefer vector chunk object when same key appears in both sources`() {
        val sut = HybridSearchAggregator(alpha = 0.5)
        val vector = listOf(hit("D", 0, 0.9, text = "vec-D0"))
        val bm25 = listOf(hit("D", 0, 10.0, text = "bm25-D0"))

        val result = sut.aggregate(vectorHits = vector, bm25Hits = bm25, k = 10)

        assertEquals(1, result.size)
        assertEquals("vec-D0", result.first().chunk.text)
    }

    @Test
    fun `should normalize equal scores per source and break ties deterministically (vector-only)`() {
        val sut = HybridSearchAggregator(alpha = 1.0)
        val vector =
            listOf(
                hit("a", 1, 42.0),
                hit("a", 0, 42.0),
                hit("b", 0, 42.0),
            )

        val result = sut.aggregate(vectorHits = vector, bm25Hits = emptyList(), k = 10)

        val ordered = result.map { it.documentId.value to it.chunk.metadata["chunk_index"] }
        assertEquals(listOf("a" to "0", "a" to "1", "b" to "0"), ordered)
    }

    @Test
    fun `should place missing chunk_index after explicit indices when scores tie`() {
        val sut = HybridSearchAggregator(alpha = 1.0)
        val vector =
            listOf(
                hit(doc = "M", idx = 0, score = 1.0, text = "with-index"),
                hitWithoutIndex(doc = "M", score = 1.0, text = "no-index"),
            )

        val result = sut.aggregate(vectorHits = vector, bm25Hits = emptyList(), k = 10)

        assertEquals("M", result[0].documentId.value)
        assertEquals("0", result[0].chunk.metadata["chunk_index"])
        assertEquals("M", result[1].documentId.value)
        assertEquals(result[1].chunk.metadata["chunk_index"], null)
        assertEquals("no-index", result[1].chunk.text)
    }

    @Test
    fun `should honor alpha extremes (1_0 only vector, 0_0 only bm25)`() {
        val sutVectorOnly = HybridSearchAggregator(alpha = 1.0)
        val sutBm25Only = HybridSearchAggregator(alpha = 0.0)
        val vector =
            listOf(
                hit("X", 0, 0.1),
                hit("Y", 0, 0.9),
            )
        val bm25 =
            listOf(
                hit("X", 0, 100.0),
                hit("Y", 0, 10.0),
            )

        val resultVectorOnly = sutVectorOnly.aggregate(vectorHits = vector, bm25Hits = bm25, k = 1)
        val resultBm25Only = sutBm25Only.aggregate(vectorHits = vector, bm25Hits = bm25, k = 1)

        assertEquals("Y", resultVectorOnly.first().documentId.value)
        assertEquals("X", resultBm25Only.first().documentId.value)
    }

    @Test
    fun `should throw on invalid alpha`() {
        assertThrows<IllegalArgumentException> { HybridSearchAggregator(alpha = -0.01) }
        assertThrows<IllegalArgumentException> { HybridSearchAggregator(alpha = 1.01) }
    }

    private fun hit(
        doc: String,
        idx: Int,
        score: Double,
        text: String = "$doc-$idx",
    ): SearchMatch {
        val chunk =
            TextChunk(
                text = text,
                metadata = mapOf("chunk_index" to idx.toString()),
            )
        return SearchMatch(
            documentId = DocumentId(doc),
            chunk = chunk,
            score = score,
        )
    }

    private fun hitWithoutIndex(
        doc: String,
        score: Double,
        text: String = "$doc-noidx",
    ): SearchMatch {
        val chunk =
            TextChunk(
                text = text,
                metadata = emptyMap(),
            )
        return SearchMatch(
            documentId = DocumentId(doc),
            chunk = chunk,
            score = score,
        )
    }
}
