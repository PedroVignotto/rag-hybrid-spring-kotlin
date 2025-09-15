package dev.pedro.rag.infra.retrieval.embed.fake

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class FakeEmbeddingProvider(
    private val embeddingSpec: EmbeddingSpec,
) : EmbedPort {
    override fun spec(): EmbeddingSpec = embeddingSpec

    override fun embed(text: String): EmbeddingVector {
        val rawValues = hashToVector(text, embeddingSpec.dim)
        return EmbeddingVector(
            values = rawValues,
            dim = embeddingSpec.dim,
            normalized = false,
        )
    }

    override fun embedAll(texts: List<String>): List<EmbeddingVector> = texts.map { embed(it) }

    private fun hashToVector(
        text: String,
        dim: Int,
    ): FloatArray {
        val bytes =
            MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(StandardCharsets.UTF_8))
        val buffer = ByteBuffer.wrap(bytes)
        val out = FloatArray(dim)
        var i = 0
        while (i < dim) {
            val intValue =
                if (buffer.remaining() >= Int.SIZE_BYTES) {
                    buffer.int
                } else {
                    buffer.rewind()
                    buffer.int
                }
            out[i] = (intValue / Int.MAX_VALUE.toFloat()).coerceIn(-1f, 1f)
            i++
        }
        return out
    }
}
