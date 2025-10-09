package dev.pedro.rag.application.retrieval.ask.mappers

import dev.pedro.rag.application.retrieval.search.dto.SearchOutput
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


class SearchOutputMapperTest {

    @Test
    fun `should map matches to ContextSource preserving order and using fallbacks`() {
        val output = SearchOutput(
            matches = listOf(
                SearchMatch(
                    documentId = DocumentId("ze-menu-001"),
                    chunk = TextChunk(text = "Tem opção vegetariana e aceita Pix."),
                    score = 0.91
                ),
                SearchMatch(
                    documentId = DocumentId("ze-promo-001"),
                    chunk = TextChunk(text = "Terças: 2x1 no smash."),
                    score = 0.84
                ),
                SearchMatch(
                    documentId = DocumentId("ze-hours-001"),
                    chunk = TextChunk(text = "Domingo 12:00–22:30."),
                    score = 0.80
                ),
            )
        )

        val result = output.toContextSources()

        assertThat(result).hasSize(3)

        assertThat(result[0].documentId).isEqualTo("ze-menu-001")
        assertThat(result[0].title).isEqualTo("ze-menu-001")
        assertThat(result[0].chunkIndex).isEqualTo(0)
        assertThat(result[0].text).isEqualTo("Tem opção vegetariana e aceita Pix.")
        assertThat(result[0].score).isEqualTo(0.91)
        assertThat(result[1].documentId).isEqualTo("ze-promo-001")
        assertThat(result[1].title).isEqualTo("ze-promo-001")
        assertThat(result[1].chunkIndex).isEqualTo(1)
        assertThat(result[1].text).isEqualTo("Terças: 2x1 no smash.")
        assertThat(result[1].score).isEqualTo(0.84)
        assertThat(result[2].documentId).isEqualTo("ze-hours-001")
        assertThat(result[2].title).isEqualTo("ze-hours-001")
        assertThat(result[2].chunkIndex).isEqualTo(2)
        assertThat(result[2].text).isEqualTo("Domingo 12:00–22:30.")
        assertThat(result[2].score).isEqualTo(0.80)
    }

    @Test
    fun `should return empty when there are no matches`() {
        val output = SearchOutput(matches = emptyList())

        val result = output.toContextSources()
        assertThat(result).isEmpty()
    }
}