package dev.pedro.rag.api.chat

import com.fasterxml.jackson.databind.JsonNode
import dev.pedro.rag.IntegrationTest
import dev.pedro.rag.api.chat.request.ChatMessageRequest
import dev.pedro.rag.api.chat.request.ChatParamsRequest
import dev.pedro.rag.api.chat.request.ChatRequest
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
            ChatRequest(
                messages = listOf(ChatMessageRequest("user", "hi")),
                params = ChatParamsRequest(temperature = 0.2, topP = 0.9, maxTokens = 32),
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
        val req = ChatRequest(messages = emptyList(), params = null)

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
        val req = ChatRequest(messages = listOf(ChatMessageRequest("user", "hi")), params = null)

        mvc.perform(
            post("/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)),
        )
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.upstreamBody").value("boom"))
    }

    @Test
    fun `POST v1_chat_stream - 200 ok, emits delta-usage-done in order`() {
        val ndjson =
            listOf(
                """{"message":{"role":"assistant","content":"Hello"},"done":false}""",
                """{"message":{"role":"assistant","content":" world"},"done":false}""",
                """{"done":true,"prompt_eval_count":12,"eval_count":34,"total_duration":123456789,"load_duration":987654}""",
            ).joinToString("\n") + "\n"
        enqueueUpstream(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-ndjson")
                .setChunkedBody(ndjson, 8),
        )

        val mvcResult =
            mvc.perform(
                post("/v1/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content(mapper.writeValueAsString(streamRequest())),
            ).andReturn()

        val events = parseSse(mvcResult.response.contentAsString)
        assertThat(events.map { it.name }).containsExactly("delta", "delta", "usage", "done")
        assertThat(events[0].data).isEqualTo("""{"content":"Hello"}""")
        assertThat(events[1].data).isEqualTo("""{"content":" world"}""")
        val usage = mapper.readTree(events[2].data)
        assertThat(usage["promptTokens"].asInt()).isEqualTo(12)
        assertThat(usage["completionTokens"].asInt()).isEqualTo(34)
        assertThat(events[3].data).isEqualTo("{}")
        assertThat(mvcResult.response.contentType).startsWith("text/event-stream")
        val recorded = takeUpstreamRequest()
        assertThat(recorded.path).isEqualTo("/api/chat")
        assertThat(recorded.method).isEqualTo("POST")
        val json: JsonNode = mapper.readTree(recorded.body.readUtf8())
        assertThat(json["stream"].asBoolean()).isTrue
        assertThat(json["keep_alive"].asText()).isNotBlank()
        assertThat(json["messages"][0]["role"].asText()).isEqualTo("user")
        assertThat(json["messages"][0]["content"].asText()).isEqualTo("hello")
    }

    @Test
    fun `POST v1_chat_stream - emits error event on upstream non-2xx`() {
        enqueueUpstream(
            MockResponse()
                .setResponseCode(502)
                .setBody("upstream bad gateway")
                .setHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE),
        )

        val mvcResult =
            mvc.perform(
                post("/v1/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content(mapper.writeValueAsString(streamRequest())),
            ).andReturn()

        val events = parseSse(mvcResult.response.contentAsString)
        assertThat(events.map { it.name }).containsExactly("error")
        val err = mapper.readTree(events[0].data)
        assertThat(err["status"].asInt()).isEqualTo(502)
        assertThat(err["upstreamBody"].asText()).isEqualTo("upstream bad gateway")
        assertThat(mvcResult.response.contentType).startsWith("text/event-stream")
        val recorded = takeUpstreamRequest()
        val json: JsonNode = mapper.readTree(recorded.body.readUtf8())
        assertThat(json["stream"].asBoolean()).isTrue
    }

    private fun streamRequest(): ChatRequest =
        ChatRequest(
            messages = listOf(ChatMessageRequest("USER", "hello")),
            params = ChatParamsRequest(temperature = 0.2, topP = 0.9, maxTokens = 32),
        )

    private data class SseEvent(val name: String, val data: String)

    private fun parseSse(body: String): List<SseEvent> {
        val out = mutableListOf<SseEvent>()
        var currentName: String? = null
        val dataBuf = StringBuilder()

        fun flush() {
            if (currentName != null) {
                out += SseEvent(currentName!!, dataBuf.toString().trim())
                currentName = null
                dataBuf.setLength(0)
            }
        }
        body.lines().forEach { line ->
            when {
                line.startsWith("event:") -> {
                    flush()
                    currentName = line.removePrefix("event:").trim()
                }
                line.startsWith("data:") -> dataBuf.append(line.removePrefix("data:").trim())
                line.isBlank() -> { /* ignora separadores */ }
            }
        }
        flush()
        return out
    }
}
