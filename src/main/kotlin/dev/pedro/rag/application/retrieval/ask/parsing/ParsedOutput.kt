package dev.pedro.rag.application.retrieval.ask.parsing

data class ParsedOutput(
    val answer: String,
    val citationNs: List<Int>,
)
