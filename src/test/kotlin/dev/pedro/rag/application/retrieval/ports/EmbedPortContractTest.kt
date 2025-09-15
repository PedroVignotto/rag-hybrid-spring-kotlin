package dev.pedro.rag.application.retrieval.ports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test
import kotlin.math.abs

abstract class EmbedPortContractTest {

    protected abstract fun sutFor(normalized: Boolean): EmbedPort

    protected open fun sut(): EmbedPort = sutFor(true)

    @Test
    fun `spec should define dimension greater than zero`() {
        val sut = sut()
        val spec = sut.spec()
        assertTrue(spec.dim > 0)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `embed returns vector with dimension from spec and consistent normalized flag`(normalized: Boolean) {
        val sut = sutFor(normalized)

        val spec = sut.spec()
        val v = sut.embed("dimension-check")

        assertEquals(spec.dim, v.dim)
        assertEquals(spec.dim, v.values.size)
        assertEquals(normalized, spec.normalized)
        assertEquals(normalized, v.normalized)

        val sumSquares = v.values.fold(0.0) { acc, f -> acc + (f * f).toDouble() }
        if (normalized) {
            assertApproximately(sumSquares, eps = 1e-5)
        } else {
            assertTrue(abs(sumSquares - 1.0) > 1e-3)
        }
    }

    @Test
    fun `embed should be consistent for the same input`() {
        val sut = sut()
        val a = sut.embed("hello world")
        val b = sut.embed("hello world")

        assertTrue(a.values.contentEquals(b.values))
        assertEquals(a.dim, b.dim)
        assertEquals(a.normalized, b.normalized)
    }

    @Test
    fun `embed should generally differ for different inputs`() {
        val sut = sut()
        val javaVec = sut.embed("java")
        val pythonVec = sut.embed("python")

        assertTrue(!javaVec.values.contentEquals(pythonVec.values))
        assertEquals(javaVec.dim, pythonVec.dim)
    }

    @Test
    fun `embedAll should map 1-1 and match per-item embed`() {
        val sut = sut()
        val inputs = listOf("a", "b", "c")
        val batch = sut.embedAll(inputs)

        assertEquals(inputs.size, batch.size)

        val singleB = sut.embed("b")
        assertTrue(batch[1].values.contentEquals(singleB.values))
        assertTrue(batch.all { it.dim == sut.spec().dim && it.normalized == sut.spec().normalized })
    }

    private fun assertApproximately(actual: Double, eps: Double) {
        assertTrue(abs(1.0 - actual) <= eps, "expected=$1.0 actual=$actual eps=$eps")
    }
}