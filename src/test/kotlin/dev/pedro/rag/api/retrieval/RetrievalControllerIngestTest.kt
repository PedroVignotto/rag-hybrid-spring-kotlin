package dev.pedro.rag.api.retrieval

import dev.pedro.rag.IntegrationTest
import dev.pedro.rag.api.retrieval.request.IngestRequest
import org.hamcrest.Matchers.greaterThan
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class RetrievalControllerIngestTest : IntegrationTest() {
    @Test
    fun `POST v1_retrieval_ingest - 200 ok end-to-end`() {
        val request =
            IngestRequest(
                documentId = "menu-2025-09",
                text = "X-Bacon: bun, 150g beef, bacon, cheese, mayo. Fries combo available.",
                metadata = mapOf("store" to "hq", "type" to "menu"),
                chunkSize = 5,
                overlap = 2,
            )

        mvc.perform(
            post("/v1/retrieval/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.documentId").value("menu-2025-09"))
            .andExpect(jsonPath("$.chunksIngested").value(greaterThan(0)))
    }

    @Test
    fun `POST v1_retrieval_ingest - 400 bean validation`() {
        val invalid =
            IngestRequest(
                documentId = "doc-1",
                text = "   ",
                metadata = null,
                chunkSize = 0,
                overlap = -1,
            )

        mvc.perform(
            post("/v1/retrieval/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(invalid)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors").exists())
    }
}
