package dev.pedro.rag.application.retrieval.search.ranking

import dev.pedro.rag.application.retrieval.search.ranking.support.TextSimilarity
import dev.pedro.rag.domain.retrieval.SearchMatch
import kotlin.math.abs

class MmrReRanker(
    private val lambda: Double,
) {
    init {
        require(lambda in 0.0..1.0) { "lambda must be in [0,1]" }
    }

    private companion object {
        const val KEY_CHUNK_INDEX = "chunk_index"
        private const val EPS = 1e-12
    }

    private val diversityWeight = 1.0 - lambda

    fun rerank(
        hits: List<SearchMatch>,
        k: Int,
    ): List<SearchMatch> {
        if (k <= 0 || hits.isEmpty()) return emptyList()
        val target = minOf(k, hits.size)
        val tokensByIndex = hits.map { TextSimilarity.tokensOf(it.chunk.text) }
        val selected = ArrayList<SearchMatch>(target)
        val selectedTokenSets = ArrayList<Set<String>>(target)
        val remaining = hits.indices.toMutableSet()
        while (selected.size < target && remaining.isNotEmpty()) {
            var bestIdx = -1
            var bestMmr = Double.NEGATIVE_INFINITY
            for (idx in remaining) {
                val candidateHit = hits[idx]
                val candidateTokens = tokensByIndex[idx]
                val similarityWithChosen = maxSimilarity(candidateTokens, selectedTokenSets)
                val candidateMmr = computeMmrScore(candidateHit.score, similarityWithChosen)
                val incumbentHit: SearchMatch? = if (bestIdx == -1) null else hits[bestIdx]
                val candidateWins =
                    mmrIsBetter(candidateMmr, bestMmr) || (mmrIsTie(candidateMmr, bestMmr) && tieBreaks(candidateHit, incumbentHit))
                if (candidateWins) {
                    bestIdx = idx
                    bestMmr = candidateMmr
                }
            }
            selected += hits[bestIdx]
            selectedTokenSets += tokensByIndex[bestIdx]
            remaining.remove(bestIdx)
        }
        return selected
    }

    private fun maxSimilarity(
        candidateTokens: Set<String>,
        chosenTokenSets: List<Set<String>>,
    ): Double {
        if (chosenTokenSets.isEmpty()) return 0.0
        var max = 0.0
        for (tokens in chosenTokenSets) {
            val s = TextSimilarity.jaccard(candidateTokens, tokens)
            if (s > max) max = s
        }
        return max
    }

    private fun computeMmrScore(
        candidateScore: Double,
        similarityWithChosen: Double,
    ): Double = lambda * candidateScore - diversityWeight * similarityWithChosen

    private fun mmrIsBetter(
        a: Double,
        b: Double,
    ): Boolean = a > b + EPS

    private fun mmrIsTie(
        a: Double,
        b: Double,
    ): Boolean = abs(a - b) <= EPS

    private fun tieBreaks(
        candidate: SearchMatch,
        incumbent: SearchMatch?,
    ): Boolean {
        incumbent ?: return true
        if (candidate.score > incumbent.score + EPS) return true
        if (abs(candidate.score - incumbent.score) > EPS) return false
        val docCmp = candidate.documentId.value.compareTo(incumbent.documentId.value)
        if (docCmp != 0) return docCmp < 0
        return chunkIndexOrMax(candidate) < chunkIndexOrMax(incumbent)
    }

    private fun chunkIndexOrMax(hit: SearchMatch): Int = hit.chunk.metadata[KEY_CHUNK_INDEX]?.toIntOrNull() ?: Int.MAX_VALUE
}
