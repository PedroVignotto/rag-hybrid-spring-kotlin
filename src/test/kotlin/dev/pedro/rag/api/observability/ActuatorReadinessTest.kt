package dev.pedro.rag.api.observability

import dev.pedro.rag.IntegrationTest
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ActuatorReadinessTest : IntegrationTest() {
    @Test
    fun `GET actuator_readiness - UP when upstream returns 200`() {
        enqueueUpstream(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"models": []}"""),
        )

        mvc.perform(
            get("/actuator/health/readiness")
                .accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `GET actuator_readiness - DOWN when upstream returns 500`() {
        enqueueUpstream(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"boom"}"""),
        )

        mvc.perform(
            get("/actuator/health/readiness")
                .accept(MediaType.APPLICATION_JSON),
        )
        mvc.perform(get("/actuator/health/readiness").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.status").value("DOWN"))
            .andExpect(jsonPath("$.components.ollama.status").value("DOWN"))
    }
}
