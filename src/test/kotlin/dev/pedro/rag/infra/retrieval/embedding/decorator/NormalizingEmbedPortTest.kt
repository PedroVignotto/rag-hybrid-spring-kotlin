package dev.pedro.rag.infra.retrieval.embedding.decorator

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.infra.retrieval.embedding.fake.FakeEmbeddingProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class NormalizingEmbedPortTest {
    @Test
    fun `should normalize when spec requires normalized vectors`() {
        val spec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 12, normalized = true)
        val base: EmbedPort = FakeEmbeddingProvider(spec)
        val sut: EmbedPort = NormalizingEmbedPort(base)

        val vector = sut.embed("normalize me")

        assertTrue(vector.normalized)
        val sumSquares = vector.values.fold(0.0) { acc, f -> acc + (f * f).toDouble() }
        assertTrue(abs(sumSquares - 1.0) <= 1e-5)
    }

    @Test
    fun `should pass-through when spec does not require normalization`() {
        val spec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 12, normalized = false)
        val base: EmbedPort = FakeEmbeddingProvider(spec)
        val sut: EmbedPort = NormalizingEmbedPort(base)

        val vector = sut.embed("raw please")

        assertFalse(vector.normalized)
    }

    @Test
    fun `embedAll should normalize all when spec requires normalized vectors`() {
        val spec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 8, normalized = true)
        val base: EmbedPort = FakeEmbeddingProvider(spec)
        val sut: EmbedPort = NormalizingEmbedPort(base)
        val inputs = listOf("a", "b", "c")

        val out = sut.embedAll(inputs)

        assertEquals(3, out.size)
        assertTrue(out.all { it.normalized })
        out.forEach { v ->
            val sumSquares = v.values.fold(0.0) { acc, f -> acc + (f * f).toDouble() }
            assertTrue(abs(sumSquares - 1.0) <= 1e-5)
        }
        val singleB = sut.embed("b")
        assertTrue(out[1].values.contentEquals(singleB.values))
    }

    @Test
    fun `embedAll should pass-through when spec does not require normalization`() {
        val spec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 8, normalized = false)
        val base: EmbedPort = FakeEmbeddingProvider(spec)
        val sut: EmbedPort = NormalizingEmbedPort(base)
        val inputs = listOf("x", "y")

        val out = sut.embedAll(inputs)

        assertEquals(2, out.size)
        assertTrue(out.all { !it.normalized })
        val singleY = sut.embed("y")
        assertTrue(out[1].values.contentEquals(singleY.values))
    }
}
