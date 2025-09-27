package dev.pedro.rag.application.retrieval.search.ranking

import dev.pedro.rag.application.retrieval.search.ranking.support.TextSimilarity
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch

class SoftDedupFilter(
    private val overlapThreshold: Double,
) {
    init {
        require(overlapThreshold in 0.0..1.0) { "overlapThreshold must be in [0,1]" }
    }

    fun filter(hits: List<SearchMatch>): List<SearchMatch> {
        if (hits.isEmpty()) return emptyList()
        val selectedByDoc = mutableMapOf<DocumentId, MutableList<Set<String>>>()
        val result = ArrayList<SearchMatch>(hits.size)
        hits.forEach { hit ->
            val docId = hit.documentId
            val tokens = TextSimilarity.tokensOf(hit.chunk.text)
            val existing = selectedByDoc.getOrPut(docId) { mutableListOf() }
            val isNearDuplicate = existing.any { TextSimilarity.jaccard(it, tokens) >= overlapThreshold }
            if (!isNearDuplicate) {
                result += hit
                existing += tokens
            }
        }
        return result
    }
}
