package dev.pedro.rag.application.retrieval.ports

import dev.pedro.rag.domain.retrieval.TextChunk

interface Chunker {
    fun split(
        text: String,
        chunkSize: Int,
        overlap: Int,
    ): List<TextChunk>
}
