package dev.pedro.rag.application.retrieval.ask.parsing.citation

import dev.pedro.rag.application.retrieval.ask.context.BuiltContext
import dev.pedro.rag.application.retrieval.ask.dto.Citation

interface CitationMapper {
    fun map(
        ns: List<Int>,
        built: BuiltContext,
    ): List<Citation>
}
