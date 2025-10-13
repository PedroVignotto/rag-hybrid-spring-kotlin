package dev.pedro.rag.api.retrieval

import dev.pedro.rag.IntegrationTest
import dev.pedro.rag.api.retrieval.request.AskRequest
import dev.pedro.rag.api.retrieval.request.IngestRequest
import okhttp3.mockwebserver.MockResponse
import org.hamcrest.Matchers.greaterThan
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AskControllerTest : IntegrationTest() {
    @Test
    fun `POST v1_retrieval_ask - 400 bean validation for blank query`() {
        val request =
            AskRequest(
                query = " ",
                topK = 10,
                filter = emptyMap(),
                lang = "pt-BR",
            )

        mvc.perform(
            post("/v1/retrieval/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errors").exists())
    }

    @Test
    fun `POST v1_retrieval_ask - 200 ok end-to-end with extractive fallback`() {
        val doc1 =
            IngestRequest(
                documentId = "ask-doc-1",
                text = "Tem opção vegetariana e aceita Pix.",
                metadata = mapOf("store" to "hq", "type" to "menu"),
                chunkSize = 16,
                overlap = 4,
            )
        val doc2 =
            IngestRequest(
                documentId = "ask-doc-2",
                text = "Terças 2x1 no smash burger.",
                metadata = mapOf("store" to "hq", "type" to "promo"),
                chunkSize = 16,
                overlap = 4,
            )
        mvc.perform(
            post("/v1/retrieval/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(doc1)),
        ).andExpect(status().isOk)
        mvc.perform(
            post("/v1/retrieval/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(doc2)),
        ).andExpect(status().isOk)
        enqueueUpstream(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"message":{"role":"assistant","content":""},"done":true}"""),
        )
        val ask =
            AskRequest(
                query = "tem veggie? promoção",
                topK = 10,
                filter = mapOf("store" to "hq"),
                lang = "pt-BR",
            )

        mvc.perform(
            post("/v1/retrieval/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(ask)),
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.answer").isNotEmpty)
            .andExpect(jsonPath("$.usedK").value(greaterThan(0)))
            .andExpect(jsonPath("$.citations.length()").value(greaterThan(0)))
            .andExpect(jsonPath("$.notes").value("extractive-fallback"))
    }

    @Test
    fun `POST v1_retrieval_ask - 200 ok no-matches when nothing relevant`() {
        val ask =
            AskRequest(
                query = "zzzzzz-no-match-123",
                topK = 10,
                filter = mapOf("store" to "__none__"),
                lang = "en",
            )

        mvc.perform(
            post("/v1/retrieval/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(ask)),
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.usedK").value(0))
            .andExpect(jsonPath("$.citations.length()").value(0))
            .andExpect(jsonPath("$.notes").value("no-matches"))
    }
}
