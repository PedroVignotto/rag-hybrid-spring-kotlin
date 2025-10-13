package dev.pedro.rag.api.retrieval.mappers

import dev.pedro.rag.api.retrieval.request.AskRequest
import dev.pedro.rag.api.retrieval.request.IngestRequest
import dev.pedro.rag.api.retrieval.request.SearchRequest
import dev.pedro.rag.api.retrieval.response.AskResponse
import dev.pedro.rag.api.retrieval.response.DeleteResponse
import dev.pedro.rag.api.retrieval.response.IngestResponse
import dev.pedro.rag.api.retrieval.response.SearchMatchResponse
import dev.pedro.rag.api.retrieval.response.SearchResponse
import dev.pedro.rag.application.retrieval.ask.dto.AskInput
import dev.pedro.rag.application.retrieval.ask.dto.AskOutput
import dev.pedro.rag.application.retrieval.ask.dto.Citation
import dev.pedro.rag.application.retrieval.delete.dto.DeleteOutput
import dev.pedro.rag.application.retrieval.ingest.dto.IngestInput
import dev.pedro.rag.application.retrieval.ingest.dto.IngestOutput
import dev.pedro.rag.application.retrieval.search.dto.SearchInput
import dev.pedro.rag.application.retrieval.search.dto.SearchOutput
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RetrievalMapperTest {
    @Test
    fun `should map IngestRequest to IngestInput with null metadata as empty map`() {
        val api =
            IngestRequest(
                documentId = "doc-1",
                text = "hello world",
                metadata = null,
                chunkSize = 800,
                overlap = 120,
            )
        val expected =
            IngestInput(
                documentId = DocumentId("doc-1"),
                text = "hello world",
                baseMetadata = emptyMap(),
                chunkSize = 800,
                overlap = 120,
            )

        val actual = api.toInput()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun `should map IngestOutput to IngestResponse`() {
        val domain = IngestOutput(documentId = DocumentId("doc-xyz"), chunksIngested = 3)
        val expected = IngestResponse(documentId = "doc-xyz", chunksIngested = 3)

        val actual = domain.toResponse()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun `should map SearchRequest to SearchInput`() {
        val api =
            SearchRequest(
                query = "x-bacon",
                topK = 5,
                filter = mapOf("store" to "hq"),
            )
        val expected =
            SearchInput(
                queryText = "x-bacon",
                topK = 5,
                filter = mapOf("store" to "hq"),
            )

        val actual = api.toInput()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun `should map SearchOutput to SearchResponse`() {
        val domain =
            SearchOutput(
                matches =
                    listOf(
                        SearchMatch(
                            documentId = DocumentId("menu-2025-09"),
                            chunk =
                                TextChunk(
                                    text = "X-Bacon: bun, 150g beef, bacon, cheese, mayo",
                                    metadata = mapOf("store" to "hq", "type" to "menu"),
                                ),
                            score = 0.92,
                        ),
                        SearchMatch(
                            documentId = DocumentId("menu-2025-09"),
                            chunk =
                                TextChunk(
                                    text = "Fries combo available",
                                    metadata = mapOf("store" to "hq", "type" to "menu"),
                                ),
                            score = 0.78,
                        ),
                    ),
            )
        val expected =
            SearchResponse(
                matches =
                    listOf(
                        SearchMatchResponse(
                            documentId = "menu-2025-09",
                            text = "X-Bacon: bun, 150g beef, bacon, cheese, mayo",
                            score = 0.92,
                            metadata = mapOf("store" to "hq", "type" to "menu"),
                        ),
                        SearchMatchResponse(
                            documentId = "menu-2025-09",
                            text = "Fries combo available",
                            score = 0.78,
                            metadata = mapOf("store" to "hq", "type" to "menu"),
                        ),
                    ),
            )

        val actual = domain.toResponse()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun `should map DeleteOutput to DeleteResponse`() {
        val domain = DeleteOutput(deleted = 2)
        val expected = DeleteResponse(deleted = 2)

        val actual = domain.toResponse()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun `should map AskRequest to AskInput`() {
        val api =
            AskRequest(
                query = "tem veggie? promoção",
                topK = 7,
                filter = mapOf("store" to "hq"),
                lang = "pt-BR",
            )
        val expected =
            AskInput(
                query = "tem veggie? promoção",
                topK = 7,
                filter = mapOf("store" to "hq"),
                lang = "pt-BR",
            )

        val actual = api.toInput()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun `should map AskOutput to AskResponse`() {
        val domain =
            AskOutput(
                answer = "Sim, há veggie e promoção. [1][2]",
                citations =
                    listOf(
                        Citation(documentId = "ze-menu-001", title = "Zé Menu", chunkIndex = 0),
                        Citation(documentId = "ze-promo-001", title = "Zé Promo", chunkIndex = 1),
                    ),
                usedK = 2,
                notes = "extractive-fallback",
            )
        val expected =
            AskResponse(
                answer = "Sim, há veggie e promoção. [1][2]",
                citations =
                    listOf(
                        AskResponse.Citation(documentId = "ze-menu-001", title = "Zé Menu", chunkIndex = 0),
                        AskResponse.Citation(documentId = "ze-promo-001", title = "Zé Promo", chunkIndex = 1),
                    ),
                usedK = 2,
                notes = "extractive-fallback",
            )

        val actual = domain.toResponse()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }
}
