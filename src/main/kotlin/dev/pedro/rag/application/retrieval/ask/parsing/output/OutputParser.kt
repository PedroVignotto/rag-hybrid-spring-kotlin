package dev.pedro.rag.application.retrieval.ask.parsing.output

interface OutputParser {
    fun parse(raw: String): ParsedOutput
}
