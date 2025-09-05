package dev.pedro.rag.api.chat

import dev.pedro.rag.IntegrationTest
import dev.pedro.rag.api.chat.request.ApiChatMessage
import dev.pedro.rag.api.chat.request.ApiChatParams
import dev.pedro.rag.api.chat.request.ApiChatRequest
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatResponse
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatResponseMessage
import okhttp3.mockwebserver.MockResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ChatControllerTest : IntegrationTest() {
    @Test
    fun `POST v1_chat - 200 ok end-to-end`() {
        enqueueUpstreamJson(
            OllamaChatResponse(
                message = OllamaChatResponseMessage(role = "assistant", content = "ok"),
                done = true,
            ),
            status = 200,
        )
        val req =
            ApiChatRequest(
                messages = listOf(ApiChatMessage("user", "hi")),
                params = ApiChatParams(temperature = 0.2, topP = 0.9, maxTokens = 32),
            )

        mvc.perform(
            post("/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)),
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.content").value("ok"))

        val recorded = takeUpstreamRequest()
        assertThat(recorded.path).isEqualTo("/api/chat")
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.getHeader("Content-Type")).isEqualTo("application/json")
    }

    @Test
    fun `POST v1_chat - 400 bean validation`() {
        val req = ApiChatRequest(messages = emptyList(), params = null)

        mvc.perform(
            post("/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors").exists())
    }

    @Test
    fun `POST v1_chat - 502 when upstream returns 500`() {
        enqueueUpstream(MockResponse().setResponseCode(500).setBody("boom"))
        val req = ApiChatRequest(messages = listOf(ApiChatMessage("user", "hi")), params = null)

        mvc.perform(
            post("/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)),
        )
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.upstreamBody").value("boom"))
    }
}
