package dev.pedro.rag.api.retrieval

import dev.pedro.rag.IntegrationTest
import dev.pedro.rag.api.retrieval.request.IngestRequest
import org.hamcrest.Matchers.greaterThan
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class RetrievalControllerDeleteTest : IntegrationTest() {
    @Test
    fun `DELETE v1_retrieval_documentId - 200 ok end-to-end and idempotent`() {
        val request =
            IngestRequest(
                documentId = "doc-delete",
                text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                metadata = null,
                chunkSize = 8,
                overlap = 2,
            )

        mvc.perform(
            post("/v1/retrieval/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        mvc.perform(delete("/v1/retrieval/doc-delete"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.deleted").value(greaterThan(0)))

        mvc.perform(delete("/v1/retrieval/doc-delete"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.deleted").value(0))
    }

    @Test
    fun `DELETE v1_retrieval_documentId - 200 ok for nonexistent id (deleted=0)`() {
        mvc.perform(delete("/v1/retrieval/does-not-exist"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.deleted").value(0))
    }

    @Test
    fun `DELETE v1_retrieval_documentId - 400 bean validation for blank id`() {
        mvc.perform(delete("/v1/retrieval/{documentId}", " "))
            .andExpect(status().isBadRequest)
    }
}
