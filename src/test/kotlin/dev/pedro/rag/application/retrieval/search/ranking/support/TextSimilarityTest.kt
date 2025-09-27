package dev.pedro.rag.application.retrieval.search.ranking.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextSimilarityTest {
    @Test
    fun `tokensOf normalizes case accents and punctuation`() {
        val result = TextSimilarity.tokensOf("Coração, DESIGN! v2.0")

        assertTrue("coracao" in result)
        assertTrue("design" in result)
        assertTrue("v2" in result)
        assertTrue("0" in result)
    }

    @Test
    fun `tokensOf returns empty for blank or punctuation-only`() {
        assertEquals(emptySet<String>(), TextSimilarity.tokensOf("   "))
        assertEquals(emptySet<String>(), TextSimilarity.tokensOf(".,; - "))
    }

    @Test
    fun `jaccard equals 1 for identical sets`() {
        val a = setOf("a", "b", "c")
        val b = setOf("a", "b", "c")

        val result = TextSimilarity.jaccard(a, b)

        assertEquals(1.0, result, 1e-9)
    }

    @Test
    fun `jaccard equals 0 for disjoint sets`() {
        val a = setOf("a")
        val b = setOf("x")

        val result = TextSimilarity.jaccard(a, b)

        assertEquals(0.0, result, 1e-9)
    }

    @Test
    fun `jaccard handles partial overlap`() {
        val a = setOf("a", "b", "c")
        val b = setOf("a", "b", "d")

        val result = TextSimilarity.jaccard(a, b)

        assertEquals(0.5, result, 1e-9)
    }

    @Test
    fun `jaccard is 0 when one side is empty`() {
        assertEquals(0.0, TextSimilarity.jaccard(emptySet(), setOf("a")), 1e-9)
        assertEquals(0.0, TextSimilarity.jaccard(setOf("a"), emptySet()), 1e-9)
    }
}
