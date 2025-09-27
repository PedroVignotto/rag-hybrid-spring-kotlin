package dev.pedro.rag.application.retrieval.search.ranking.support

import java.text.Normalizer

object TextSimilarity {
    fun tokensOf(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val noAccentsLower =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
        val cleaned = noAccentsLower.replace("[^\\p{L}\\p{Nd}]".toRegex(), " ")
        return cleaned.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun jaccard(
        a: Set<String>,
        b: Set<String>,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.intersect(b).size.toDouble()
        val union = (a.size + b.size - inter)
        return inter / union
    }
}
