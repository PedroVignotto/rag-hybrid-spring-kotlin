package dev.pedro.rag.application.retrieval.search.ranking

import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk

class HybridSearchAggregator(
    private val alpha: Double,
) {
    init {
        require(alpha in 0.0..1.0) { "alpha must be in [0,1]" }
    }

    private companion object {
        const val KEY_CHUNK_INDEX = "chunk_index"
        private const val EPS = 1e-12
    }

    private data class DedupKey(val documentId: DocumentId, val chunkIndex: Int)

    fun aggregate(
        vectorHits: List<SearchMatch>,
        bm25Hits: List<SearchMatch>,
        k: Int,
    ): List<SearchMatch> {
        if (k <= 0) return emptyList()
        if (vectorHits.isEmpty() && bm25Hits.isEmpty()) return emptyList()
        val vectorByKey = indexByDedupKey(vectorHits)
        val bm25ByKey = indexByDedupKey(bm25Hits)
        val vectorNorm = normalizeScoresForSource(vectorHits)
        val bm25Norm = normalizeScoresForSource(bm25Hits)
        val keys = strictDedupByKey(vectorByKey, bm25ByKey)
        val fused =
            keys.map { key ->
                val score = calculateScoreForKey(key, vectorNorm, bm25Norm)
                val (docId, chunk) = retrieveChunkForKey(key, vectorByKey, bm25ByKey)
                SearchMatch(documentId = docId, chunk = chunk, score = score)
            }
        return sortDeterministic(fused).take(k)
    }

    private fun indexByDedupKey(hits: List<SearchMatch>): Map<DedupKey, SearchMatch> = hits.associateBy(::dedupKeyOf)

    private fun strictDedupByKey(
        a: Map<DedupKey, SearchMatch>,
        b: Map<DedupKey, SearchMatch>,
    ): Set<DedupKey> = (a.keys + b.keys).toSet()

    private fun calculateScoreForKey(
        key: DedupKey,
        vNorm: Map<DedupKey, Double>,
        bNorm: Map<DedupKey, Double>,
    ): Double {
        val v = vNorm[key] ?: 0.0
        val b = bNorm[key] ?: 0.0
        return alpha * v + (1.0 - alpha) * b
    }

    private fun retrieveChunkForKey(
        key: DedupKey,
        vectorByKey: Map<DedupKey, SearchMatch>,
        bm25ByKey: Map<DedupKey, SearchMatch>,
    ): Pair<DocumentId, TextChunk> {
        val from = vectorByKey[key] ?: bm25ByKey[key]
        requireNotNull(from) { "at least one source must provide the hit" }
        return from.documentId to from.chunk
    }

    private fun normalizeScoresForSource(hits: List<SearchMatch>): Map<DedupKey, Double> {
        if (hits.isEmpty()) return emptyMap()
        val min = hits.minOf { it.score }
        val max = hits.maxOf { it.score }
        val range = max - min
        return if (range <= EPS) {
            hits.associate { dedupKeyOf(it) to 1.0 }
        } else {
            hits.associate { dedupKeyOf(it) to ((it.score - min) / range) }
        }
    }

    private fun sortDeterministic(hits: List<SearchMatch>): List<SearchMatch> =
        hits.sortedWith(
            compareByDescending<SearchMatch> { it.score }
                .thenBy { it.documentId.value }
                .thenBy { chunkIndexOf(it) },
        )

    private fun dedupKeyOf(hit: SearchMatch): DedupKey = DedupKey(hit.documentId, chunkIndexOf(hit))

    private fun chunkIndexOf(hit: SearchMatch): Int = hit.chunk.metadata[KEY_CHUNK_INDEX]?.toIntOrNull() ?: Int.MAX_VALUE
}
