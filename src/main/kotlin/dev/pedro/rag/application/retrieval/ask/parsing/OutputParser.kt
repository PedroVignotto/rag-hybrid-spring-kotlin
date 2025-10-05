package dev.pedro.rag.application.retrieval.ask.parsing

interface OutputParser {
    fun parse(raw: String): ParsedOutput
}
