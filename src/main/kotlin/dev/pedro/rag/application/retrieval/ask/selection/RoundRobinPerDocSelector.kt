package dev.pedro.rag.application.retrieval.ask.selection

import dev.pedro.rag.application.retrieval.ask.context.ContextSource
import kotlin.math.min

internal class RoundRobinPerDocSelector : ContextSelector {
    override fun select(
        sources: List<ContextSource>,
        topK: Int,
        maxChunksPerDoc: Int,
    ): List<ContextSource> {
        require(topK >= 0) { "topK must be >= 0" }
        require(maxChunksPerDoc > 0) { "maxChunksPerDoc must be > 0" }
        if (topK == 0 || sources.isEmpty()) return emptyList()
        val capacity = min(topK, sources.size)
        val result = ArrayList<ContextSource>(capacity)
        val byDoc = bucketByDocument(sources)
        while (result.size < capacity) {
            val remaining = capacity - result.size
            val round = nextRound(byDoc, result, maxChunksPerDoc)
            if (round.isEmpty()) break
            result.addAll(round.take(remaining))
        }
        return result
    }

    private fun bucketByDocument(sources: List<ContextSource>): LinkedHashMap<String, List<ContextSource>> {
        val byDoc = LinkedHashMap<String, MutableList<ContextSource>>()
        for (s in sources) byDoc.computeIfAbsent(s.documentId) { mutableListOf() }.add(s)
        return LinkedHashMap(byDoc.mapValues { it.value.toList() })
    }

    private fun nextRound(
        byDoc: LinkedHashMap<String, List<ContextSource>>,
        result: List<ContextSource>,
        maxChunksPerDoc: Int,
    ): List<ContextSource> {
        val round = ArrayList<ContextSource>(byDoc.size)
        for ((docId, list) in byDoc) {
            val alreadyTaken = result.count { it.documentId == docId }
            if (alreadyTaken >= maxChunksPerDoc) continue
            if (alreadyTaken < list.size) {
                round.add(list[alreadyTaken])
            }
        }
        return round
    }
}
