package dev.pedro.rag.infra.retrieval.vectorstore.support

import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch
import kotlin.math.min
import kotlin.math.sqrt

object VectorSearchSupport {
    fun cosineSimilarity(
        a: EmbeddingVector,
        b: EmbeddingVector,
    ): Double {
        if (a.normalized && b.normalized) {
            return dotProduct(a.values, b.values).toDouble()
        }
        val numerator = dotProduct(a.values, b.values).toDouble()
        val normA = euclideanNorm(a.values)
        val normB = euclideanNorm(b.values)
        if (normA == 0.0 || normB == 0.0) return 0.0
        return numerator / (normA * normB)
    }

    fun dotProduct(
        x: FloatArray,
        y: FloatArray,
    ): Float {
        val n = min(x.size, y.size)
        var sum = 0f
        for (i in 0 until n) sum += x[i] * y[i]
        return sum
    }

    fun euclideanNorm(x: FloatArray): Double {
        var sumSquares = 0.0
        for (v in x) sumSquares += (v * v).toDouble()
        return sqrt(sumSquares)
    }

    fun sortByScoreAndStability(matches: List<SearchMatch>): List<SearchMatch> =
        matches.sortedWith(
            compareByDescending<SearchMatch> { it.score }
                .thenBy { it.documentId.value }
                .thenBy { it.chunk.metadata["chunk_index"]?.toIntOrNull() ?: Int.MAX_VALUE },
        )

    fun matchesMetadata(
        metadata: Map<String, String>,
        filter: Map<String, String>?,
    ): Boolean {
        if (filter.isNullOrEmpty()) return true
        return filter.all { (key, expected) -> metadata[key] == expected }
    }
}
