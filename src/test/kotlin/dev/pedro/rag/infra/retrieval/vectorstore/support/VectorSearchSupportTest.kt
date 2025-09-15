package dev.pedro.rag.infra.retrieval.vectorstore.support

import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class VectorSearchSupportTest {
    private val sut = VectorSearchSupport

    @Test
    fun `cosineSimilarity returns dot when both vectors are normalized`() {
        val a = vec(floatArrayOf(1f, 0f, 0f), normalized = true)
        val b = vec(floatArrayOf(0.6f, 0.8f, 0f), normalized = true)

        val cosine = sut.cosineSimilarity(a, b)
        val dot = sut.dotProduct(a.values, b.values).toDouble()

        assertApprox(dot, cosine)
    }

    @Test
    fun `cosineSimilarity computes full cosine when vectors are not normalized`() {
        val a = vec(floatArrayOf(1f, 0f), normalized = false)
        val b = vec(floatArrayOf(1f, 1f), normalized = false)

        val cosine = sut.cosineSimilarity(a, b)

        assertApprox(0.70710678, cosine, eps = 1e-6)
    }

    @Test
    fun `cosineSimilarity should return 0 when one vector has zero norm`() {
        val zero = vec(floatArrayOf(0f, 0f, 0f), normalized = false)
        val nonZero = vec(floatArrayOf(1f, 0f, 0f), normalized = false)

        val cosine = sut.cosineSimilarity(zero, nonZero)

        assertApprox(0.0, cosine)
    }

    @Test
    fun `matchesMetadata returns true only for exact AND matches and treats null or empty as no filter`() {
        val metadata = mapOf("location" to "hq", "type" to "menu")

        val okNull = sut.matchesMetadata(metadata, null)
        val okEmpty = sut.matchesMetadata(metadata, emptyMap())
        val okAnd = sut.matchesMetadata(metadata, mapOf("location" to "hq", "type" to "menu"))
        val notOk = sut.matchesMetadata(metadata, mapOf("location" to "branch", "type" to "menu"))

        assertTrue(okNull)
        assertTrue(okEmpty)
        assertTrue(okAnd)
        assertTrue(!notOk)
    }

    @Test
    fun `sortByScoreAndStability orders by score then by documentId then by chunk_index`() {
        val m1 = match(docId = "docA", chunkIndex = 1, score = 0.9)
        val m2 = match(docId = "docA", chunkIndex = 0, score = 0.9)
        val m3 = match(docId = "docB", chunkIndex = 5, score = 0.95)
        val m4 = match(docId = "docA", chunkIndex = 2, score = 0.9)

        val sorted = sut.sortByScoreAndStability(listOf(m1, m2, m3, m4))

        assertEquals("docB", sorted[0].documentId.value)
        assertEquals(listOf(0, 1, 2), sorted.drop(1).map { it.chunk.metadata["chunk_index"]!!.toInt() })
    }

    @Test
    fun `sortByScoreAndStability should break ties by documentId then by chunk_index`() {
        val tieA0 = match(docId = "docA", chunkIndex = 0, score = 0.9)
        val tieA1 = match(docId = "docA", chunkIndex = 1, score = 0.9)
        val tieB0 = match(docId = "docB", chunkIndex = 0, score = 0.9)

        val sorted = sut.sortByScoreAndStability(listOf(tieB0, tieA1, tieA0))

        assertEquals(listOf("docA", "docA", "docB"), sorted.map { it.documentId.value })
        assertEquals(listOf(0, 1, 0), sorted.map { it.chunk.metadata["chunk_index"]!!.toInt() })
    }

    @Test
    fun `sortByScoreAndStability should place entries with missing chunk_index after those with it`() {
        val withIndex = match(docId = "docA", chunkIndex = 0, score = 0.9)
        val missingIndex = matchMissingIndex()

        val sorted = sut.sortByScoreAndStability(listOf(missingIndex, withIndex))

        assertEquals(listOf("docA", "docA"), sorted.map { it.documentId.value })
        assertEquals(
            listOf(0, Int.MAX_VALUE),
            sorted.map { it.chunk.metadata["chunk_index"]?.toIntOrNull() ?: Int.MAX_VALUE },
        )
    }

    @Test
    fun `dotProduct and euclideanNorm basic sanity`() {
        val x = floatArrayOf(3f, 4f)
        val y = floatArrayOf(3f, 4f)

        val dot = sut.dotProduct(x, y)
        val norm = sut.euclideanNorm(x)

        assertEquals(25f, dot)
        assertApprox(5.0, norm)
    }

    @Test
    fun `dotProduct should use min length when arrays have different sizes`() {
        val sut = VectorSearchSupport
        val x = floatArrayOf(1f, 2f, 3f)
        val y = floatArrayOf(10f, 10f)

        val dot = sut.dotProduct(x, y)

        assertEquals(30f, dot)
    }

    private fun vec(
        values: FloatArray,
        normalized: Boolean,
    ) = EmbeddingVector(values = values, dim = values.size, normalized = normalized)

    private fun match(
        docId: String,
        chunkIndex: Int,
        score: Double,
    ): SearchMatch =
        SearchMatch(
            documentId = DocumentId(docId),
            chunk =
                TextChunk(
                    text = "dummy",
                    metadata = mapOf("chunk_index" to chunkIndex.toString()),
                ),
            score = score,
        )

    private fun matchMissingIndex(): SearchMatch =
        SearchMatch(
            documentId = DocumentId("docA"),
            chunk = TextChunk(text = "dummy", metadata = emptyMap()),
            score = 0.9,
        )

    private fun assertApprox(
        expected: Double,
        actual: Double,
        eps: Double = 1e-9,
    ) {
        assertTrue(abs(expected - actual) <= eps, "expected=$expected actual=$actual eps=$eps")
    }
}
