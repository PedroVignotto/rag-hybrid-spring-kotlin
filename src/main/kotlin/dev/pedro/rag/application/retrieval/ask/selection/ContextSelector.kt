package dev.pedro.rag.application.retrieval.ask.selection

import dev.pedro.rag.application.retrieval.ask.context.ContextSource

interface ContextSelector {
    fun select(
        sources: List<ContextSource>,
        topK: Int,
        maxChunksPerDoc: Int,
    ): List<ContextSource>
}
