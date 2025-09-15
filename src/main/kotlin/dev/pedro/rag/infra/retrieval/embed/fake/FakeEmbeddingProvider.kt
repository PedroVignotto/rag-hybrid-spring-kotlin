package dev.pedro.rag.infra.retrieval.embed.fake

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.sqrt

class FakeEmbeddingProvider(
    private val embeddingSpec: EmbeddingSpec
) : EmbedPort {

    override fun spec(): EmbeddingSpec = embeddingSpec

    override fun embed(text: String): EmbeddingVector {
        val rawValues = hashToVector(text, embeddingSpec.dim)
        val normalizedValues = if (embeddingSpec.normalized) normalize(rawValues) else rawValues
        return EmbeddingVector(
            values = normalizedValues,
            dim = embeddingSpec.dim,
            normalized = embeddingSpec.normalized
        )
    }

    override fun embedAll(texts: List<String>): List<EmbeddingVector> =
        texts.map { embed(it) }

    private fun hashToVector(text: String, dim: Int): FloatArray {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
        val buffer = ByteBuffer.wrap(bytes)
        val output = FloatArray(dim)
        var index = 0
        while (index < dim) {
            val intValue = if (buffer.remaining() >= Int.SIZE_BYTES) {
                buffer.int
            } else {
                buffer.rewind()
                buffer.int
            }
            output[index] = (intValue / Int.MAX_VALUE.toFloat()).coerceIn(-1f, 1f)
            index++
        }
        return output
    }

    private fun normalize(values: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (value in values) sumSquares += (value * value).toDouble()
        val norm = sqrt(sumSquares)
        if (norm == 0.0) return values
        val inverse = 1.0 / norm
        val normalized = FloatArray(values.size)
        for (i in values.indices) normalized[i] = (values[i] * inverse).toFloat()
        return normalized
    }
}