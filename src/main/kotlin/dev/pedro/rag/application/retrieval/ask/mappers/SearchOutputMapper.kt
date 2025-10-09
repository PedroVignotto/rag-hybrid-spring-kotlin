package dev.pedro.rag.application.retrieval.ask.mappers

import dev.pedro.rag.application.retrieval.ask.context.ContextSource
import dev.pedro.rag.application.retrieval.search.dto.SearchOutput

internal fun SearchOutput.toContextSources(): List<ContextSource> =
    this.matches.withIndex().map { (i, m) ->
        ContextSource(
            documentId = m.documentId.value,
            title = m.documentId.value,
            chunkIndex = i,
            text = m.chunk.text,
            score = m.score
        )
    }