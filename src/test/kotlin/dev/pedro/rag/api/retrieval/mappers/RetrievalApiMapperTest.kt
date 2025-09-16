package dev.pedro.rag.api.retrieval.mappers

import dev.pedro.rag.api.retrieval.request.IngestRequest
import dev.pedro.rag.api.retrieval.response.IngestResponse
import dev.pedro.rag.api.retrieval.response.SearchMatchResponse
import dev.pedro.rag.api.retrieval.response.SearchResponse
import dev.pedro.rag.application.retrieval.usecase.ingest.IngestResult
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RetrievalApiMapperTest {

    @Test
    fun `should map IngestRequest to IngestCommand with null metadata as empty map`() {
        val api = IngestRequest(
            documentId = "doc-1",
            text = "hello world",
            metadata = null,
            chunkSize = 800,
            overlap = 120
        )
        val expected = dev.pedro.rag.application.retrieval.usecase.ingest.IngestCommand(
            documentId = DocumentId("doc-1"),
            text = "hello world",
            baseMetadata = emptyMap(),
            chunkSize = 800,
            overlap = 120
        )

        val actual = api.toCommand()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun `should map IngestResult (domain) to IngestResponse (api)`() {
        val domain = IngestResult(documentId = DocumentId("doc-xyz"), chunksIngested = 3)
        val expected = IngestResponse(documentId = "doc-xyz", chunksIngested = 3)

        val actual = domain.toResponse()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun `should map list of SearchMatch (domain) to SearchResponse (api)`() {
        val domain = listOf(
            SearchMatch(
                documentId = DocumentId("menu-2025-09"),
                chunk = TextChunk(
                    text = "X-Bacon: bun, 150g beef, bacon, cheese, mayo",
                    metadata = mapOf("store" to "hq", "type" to "menu")
                ),
                score = 0.92
            ),
            SearchMatch(
                documentId = DocumentId("menu-2025-09"),
                chunk = TextChunk(
                    text = "Fries combo available",
                    metadata = mapOf("store" to "hq", "type" to "menu")
                ),
                score = 0.78
            )
        )
        val expected = SearchResponse(
            matches = listOf(
                SearchMatchResponse(
                    documentId = "menu-2025-09",
                    text = "X-Bacon: bun, 150g beef, bacon, cheese, mayo",
                    score = 0.92,
                    metadata = mapOf("store" to "hq", "type" to "menu")
                ),
                SearchMatchResponse(
                    documentId = "menu-2025-09",
                    text = "Fries combo available",
                    score = 0.78,
                    metadata = mapOf("store" to "hq", "type" to "menu")
                )
            )
        )

        val actual = domain.toResponse()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }
}