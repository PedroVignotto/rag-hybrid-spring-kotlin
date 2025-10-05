package dev.pedro.rag.application.retrieval.ask.parsing.output

import dev.pedro.rag.application.retrieval.ask.parsing.output.ParsedOutput

interface OutputParser {
    fun parse(raw: String): ParsedOutput
}