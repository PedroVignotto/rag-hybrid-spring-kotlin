package dev.pedro.rag.application.retrieval.ask.prompt

data class PromptPayload(
    val system: String,
    val user: String,
)
