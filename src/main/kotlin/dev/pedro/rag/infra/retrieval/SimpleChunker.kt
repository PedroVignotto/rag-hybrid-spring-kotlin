package dev.pedro.rag.infra.retrieval

import dev.pedro.rag.application.retrieval.ports.Chunker
import dev.pedro.rag.domain.retrieval.TextChunk

class SimpleChunker : Chunker {
    companion object {
        const val META_CHUNK_INDEX = "chunk_index"
        const val META_CHUNK_TOTAL = "chunk_total"
        private const val MIN_CHUNK_SIZE = 1
        private const val MIN_OVERLAP = 0
    }

    override fun split(
        text: String,
        chunkSize: Int,
        overlap: Int,
    ): List<TextChunk> {
        if (text.isBlank()) return emptyList()
        validatePreconditions(chunkSize, overlap)
        val windowStep = calculateWindowStep(chunkSize, overlap)
        val startPositions = generateStartPositions(textLength = text.length, step = windowStep)
        return startPositions.mapIndexed { chunkIndex, startIndex ->
            val endExclusive = (startIndex + chunkSize).coerceAtMost(text.length)
            val chunkText = text.substring(startIndex, endExclusive)
            TextChunk(
                text = chunkText,
                metadata = buildMetadata(chunkIndex, startPositions.size),
            )
        }
    }

    private fun validatePreconditions(
        chunkSize: Int,
        overlap: Int,
    ) {
        check(chunkSize >= MIN_CHUNK_SIZE) { "chunkSize must be >= $MIN_CHUNK_SIZE." }
        check(overlap in MIN_OVERLAP until chunkSize) {
            "overlap must be in [$MIN_OVERLAP, chunkSize - 1]."
        }
    }

    private fun calculateWindowStep(
        chunkSize: Int,
        overlap: Int,
    ): Int = chunkSize - overlap

    private fun generateStartPositions(
        textLength: Int,
        step: Int,
    ): List<Int> = (0 until textLength step step).toList()

    private fun buildMetadata(
        chunkIndex: Int,
        chunkTotal: Int,
    ): Map<String, String> =
        mapOf(
            META_CHUNK_INDEX to chunkIndex.toString(),
            META_CHUNK_TOTAL to chunkTotal.toString(),
        )
}
