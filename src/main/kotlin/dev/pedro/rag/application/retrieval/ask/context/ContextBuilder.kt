package dev.pedro.rag.application.retrieval.ask.context

interface ContextBuilder {
    fun build(
        sources: List<ContextSource>,
        budgetChars: Int,
        maxChunksPerDoc: Int,
    ): BuiltContext
}
