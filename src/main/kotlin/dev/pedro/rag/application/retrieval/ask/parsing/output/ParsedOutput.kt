package dev.pedro.rag.application.retrieval.ask.parsing.output

data class ParsedOutput(
    val answer: String,
    val citationNs: List<Int>,
)
