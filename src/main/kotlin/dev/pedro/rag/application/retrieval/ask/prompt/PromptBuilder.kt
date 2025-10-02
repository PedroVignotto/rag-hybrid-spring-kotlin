package dev.pedro.rag.application.retrieval.ask.prompt

import dev.pedro.rag.application.retrieval.ask.context.BuiltContext

interface PromptBuilder {
    fun build(
        context: BuiltContext,
        query: String,
        lang: String?,
    ): PromptPayload
}
