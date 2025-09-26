package dev.pedro.rag.application.retrieval.search.ranking

import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import java.text.Normalizer

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
            val tokens = tokensOf(hit.chunk.text)
            val existing = selectedByDoc.getOrPut(docId) { mutableListOf() }
            val isNearDuplicate = existing.any { jaccard(it, tokens) >= overlapThreshold }
            if (!isNearDuplicate) {
                result += hit
                existing += tokens
            }
        }
        return result
    }

    private fun tokensOf(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val noAccentsLower =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
        val cleaned = noAccentsLower.replace("[^\\p{L}\\p{Nd}]".toRegex(), " ")
        return cleaned.trim().split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun jaccard(
        a: Set<String>,
        b: Set<String>,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.intersect(b).size.toDouble()
        val unionSize = (a.size + b.size - inter)
        return inter / unionSize
    }
}
