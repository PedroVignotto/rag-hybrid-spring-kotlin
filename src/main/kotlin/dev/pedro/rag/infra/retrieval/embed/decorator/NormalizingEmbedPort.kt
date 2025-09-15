package dev.pedro.rag.infra.retrieval.embed.decorator

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import kotlin.math.sqrt

class NormalizingEmbedPort(
    private val delegate: EmbedPort,
) : EmbedPort {
    override fun spec(): EmbeddingSpec = delegate.spec()

    override fun embed(text: String): EmbeddingVector {
        val spec = spec()
        val raw = delegate.embed(text)
        return if (spec.normalized && !raw.normalized) {
            EmbeddingVector(
                values = l2Normalize(raw.values),
                dim = raw.dim,
                normalized = true,
            )
        } else {
            raw
        }
    }

    override fun embedAll(texts: List<String>): List<EmbeddingVector> {
        val spec = spec()
        val rawList = delegate.embedAll(texts)
        if (!spec.normalized) return rawList
        return rawList.map { vector ->
            if (vector.normalized) {
                vector
            } else {
                EmbeddingVector(
                    values = l2Normalize(vector.values),
                    dim = vector.dim,
                    normalized = true,
                )
            }
        }
    }

    private fun l2Normalize(values: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (x in values) sumSquares += (x * x).toDouble()
        val norm = sqrt(sumSquares)
        if (norm == 0.0) return values
        val inv = 1.0 / norm
        val out = FloatArray(values.size)
        for (i in values.indices) out[i] = (values[i] * inv).toFloat()
        return out
    }
}
