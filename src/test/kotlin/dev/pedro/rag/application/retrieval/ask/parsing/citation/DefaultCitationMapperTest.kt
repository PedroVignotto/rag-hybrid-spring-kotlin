package dev.pedro.rag.application.retrieval.ask.parsing.citation

import dev.pedro.rag.application.retrieval.ask.context.BuiltContext
import dev.pedro.rag.application.retrieval.ask.context.CitationIndex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefaultCitationMapperTest {
    private val sut = DefaultCitationMapper()

    @Test
    fun `should map ns to citations preserving order and deduplicating`() {
        val built =
            BuiltContext(
                text = "[1] A\n\n[2] B\n\n[3] C",
                index =
                    listOf(
                        CitationIndex(1, "doc-1", "Title 1", 0),
                        CitationIndex(2, "doc-2", "Title 2", 3),
                        CitationIndex(3, "doc-3", "Title 3", 1),
                    ),
                usedK = 3,
                truncated = false,
            )

        val result = sut.map(listOf(2, 1, 2, 3, 3, 1), built)

        assertThat(result.map { it.documentId }).containsExactly("doc-2", "doc-1", "doc-3")
        assertThat(result.map { it.title }).containsExactly("Title 2", "Title 1", "Title 3")
        assertThat(result.map { it.chunkIndex }).containsExactly(3, 0, 1)
    }

    @Test
    fun `should ignore markers not present in context index`() {
        val built =
            BuiltContext(
                text = "[1] A",
                index = listOf(CitationIndex(1, "d1", "T1", 0)),
                usedK = 1,
                truncated = false,
            )

        val result = sut.map(listOf(99, 1, 100), built)

        assertThat(result.map { it.documentId }).containsExactly("d1")
        assertThat(result).hasSize(1)
    }

    @Test
    fun `should return empty when ns is empty`() {
        val built =
            BuiltContext(
                text = "",
                index = emptyList(),
                usedK = 0,
                truncated = false,
            )

        val result = sut.map(emptyList(), built)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should return empty when context index is empty`() {
        val built =
            BuiltContext(
                text = "[1] A",
                index = emptyList(),
                usedK = 0,
                truncated = false,
            )

        val result = sut.map(listOf(1, 2), built)

        assertThat(result).isEmpty()
    }
}
