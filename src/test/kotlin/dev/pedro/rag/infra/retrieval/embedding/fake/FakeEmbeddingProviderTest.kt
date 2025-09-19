package dev.pedro.rag.infra.retrieval.embedding.fake

import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class FakeEmbeddingProviderTest {
    @Test
    fun `should be deterministic and non-normalized`() {
        val spec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 8, normalized = true)
        val sut = FakeEmbeddingProvider(spec)

        val a = sut.embed("hello")
        val b = sut.embed("hello")
        val c = sut.embed("world")

        assertArrayEquals(a.values, b.values)
        assertEquals(8, a.dim)
        assertFalse(a.normalized)
        assertNotEquals(a.values.contentHashCode(), c.values.contentHashCode())
    }
}
