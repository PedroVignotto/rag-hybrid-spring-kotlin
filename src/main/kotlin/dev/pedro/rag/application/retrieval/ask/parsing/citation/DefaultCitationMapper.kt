package dev.pedro.rag.application.retrieval.ask.parsing.citation

import dev.pedro.rag.application.retrieval.ask.context.BuiltContext
import dev.pedro.rag.application.retrieval.ask.dto.Citation

internal class DefaultCitationMapper : CitationMapper {
    override fun map(
        ns: List<Int>,
        built: BuiltContext,
    ): List<Citation> {
        if (ns.isEmpty() || built.index.isEmpty()) return emptyList()
        val byN = built.index.associateBy { it.n }
        val out = ArrayList<Citation>(ns.size)
        val seen = HashSet<Int>()
        for (n in ns) {
            if (!seen.add(n)) continue
            val ci = byN[n] ?: continue
            out.add(
                Citation(
                    documentId = ci.documentId,
                    title = ci.title,
                    chunkIndex = ci.chunkIndex,
                ),
            )
        }
        return out
    }
}
