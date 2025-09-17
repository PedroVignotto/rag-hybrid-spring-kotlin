package dev.pedro.rag.infra.llm.ollama.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.config.llm.LlmProperties
import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatMessageRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatOptionsRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatResponse
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatResponseMessage
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatStreamChunkResponse
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.Test
import kotlin.test.assertFailsWith

@SpringBootTest
@ActiveProfiles("test")
class OllamaClientTest {
    companion object {
        private val server = MockWebServer()
        private const val MODEL = "llama3.2:3b"

        @JvmStatic @BeforeAll
        fun start() {
            server.start()
        }

        @JvmStatic @AfterAll
        fun stop() {
            server.shutdown()
        }

        @JvmStatic
        @DynamicPropertySource
        fun register(reg: DynamicPropertyRegistry) {
            reg.add("llm.ollama.base-url") { server.url("/").toString() }
        }
    }

    @Autowired lateinit var mapper: ObjectMapper

    @Autowired lateinit var props: LlmProperties

    @Autowired lateinit var sut: OllamaClient

    @Test
    fun `should POST to api-chat and return parsed content with keep_alive injected`() {
        val body =
            mapper.writeValueAsString(
                OllamaChatResponse(
                    message = OllamaChatResponseMessage(role = "assistant", content = "ok"),
                    done = true,
                ),
            )
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val response =
            sut.chat(
                OllamaChatRequest(
                    model = MODEL,
                    messages = listOf(OllamaChatMessageRequest("user", "hi")),
                    options = OllamaChatOptionsRequest(temperature = 0.2, topP = 0.9, numPredict = 32),
                ),
            )

        val (recorded, json) = captureRequestJson()
        assertThat(response.message?.content).isEqualTo("ok")
        assertThat(recorded.path).isEqualTo("/api/chat")
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.getHeader("Content-Type")).isEqualTo("application/json")
        assertThat(json["model"].asText()).isEqualTo(MODEL)
        assertThat(json["keep_alive"].asText()).isEqualTo(props.ollama.keepAlive)
        assertThat(json["messages"][0]["role"].asText()).isEqualTo("user")
        assertThat(json["messages"][0]["content"].asText()).isEqualTo("hi")
        assertThat(json["options"]["top_p"].asDouble()).isEqualTo(0.9)
        assertThat(json["options"]["num_predict"].asInt()).isEqualTo(32)
        assertThat(json["stream"]).isNull()
    }

    @Test
    fun `should ignore payload keepAlive and use config keep_alive`() {
        val okBody =
            mapper.writeValueAsString(
                OllamaChatResponse(message = OllamaChatResponseMessage("assistant", "ok"), done = true),
            )
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))

        sut.chat(
            OllamaChatRequest(
                model = MODEL,
                messages = listOf(OllamaChatMessageRequest("user", "hi")),
                keepAlive = "1s",
            ),
        )

        val (_, json) = captureRequestJson()
        assertThat(json["keep_alive"].asText()).isEqualTo(props.ollama.keepAlive)
    }

    @Test
    fun `should throw OllamaHttpException on non-2xx`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val response =
            assertFailsWith<OllamaHttpException> {
                sut.chat(
                    OllamaChatRequest(
                        model = MODEL,
                        messages = listOf(OllamaChatMessageRequest("user", "x")),
                    ),
                )
            }
        assertThat(response.status).isEqualTo(500)
        assertThat(response.responseBody).contains("boom")
        server.takeRequest()
    }

    @Test
    fun `should throw when response missing message content`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"message":{}}"""))

        val response =
            assertFailsWith<OllamaInvalidResponseException> {
                sut.chat(
                    OllamaChatRequest(
                        model = MODEL,
                        messages = listOf(OllamaChatMessageRequest("user", "x")),
                    ),
                )
            }
        assertThat(response).hasMessage("Ollama response is missing `message.content`")
        server.takeRequest()
    }

    @Test
    fun `should POST with stream true and emit deltas then done chunk`() {
        val ndjson =
            listOf(
                """{"message":{"role":"assistant","content":"Hello"},"done":false}""",
                """{"message":{"role":"assistant","content":" world"},"done":false}""",
                """{"done":true,"prompt_eval_count":12,"eval_count":34,"total_duration":9999999,"load_duration":777}""",
            ).joinToString("\n")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody(ndjson),
        )
        val deltas = mutableListOf<String>()
        var last: OllamaChatStreamChunkResponse? = null

        sut.chatStream(
            payload =
                OllamaChatRequest(
                    model = MODEL,
                    messages = listOf(OllamaChatMessageRequest("user", "stream please")),
                    options = OllamaChatOptionsRequest(temperature = 0.1),
                ),
            onDelta = { deltas += it },
            onDoneChunk = { last = it },
        )

        val (recorded, json) = captureRequestJson()
        assertThat(recorded.path).isEqualTo("/api/chat")
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.getHeader("Content-Type")).isEqualTo("application/json")
        assertThat(json["model"].asText()).isEqualTo(MODEL)
        assertThat(json["keep_alive"].asText()).isEqualTo(props.ollama.keepAlive)
        assertThat(json["stream"].asBoolean()).isTrue
        assertThat(deltas).containsExactly("Hello", " world")
        val lastChunk = last
        assertThat(lastChunk).isNotNull
        assertThat(lastChunk!!.done).isTrue
        assertThat(lastChunk.promptEvalCount).isEqualTo(12)
        assertThat(lastChunk.evalCount).isEqualTo(34)
    }

    @Test
    fun `should throw OllamaHttpException on non-2xx stream`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("upstream bad gateway"))

        val response =
            assertFailsWith<OllamaHttpException> {
                sut.chatStream(
                    payload =
                        OllamaChatRequest(
                            model = MODEL,
                            messages = listOf(OllamaChatMessageRequest("user", "stream error")),
                        ),
                    onDelta = {},
                )
            }
        assertThat(response.status).isEqualTo(502)
        assertThat(response.responseBody).contains("upstream bad gateway")
        server.takeRequest()
    }

    @Test
    fun `should POST with stream true and emit deltas without onDoneChunk`() {
        val ndjson =
            listOf(
                """{"message":{"role":"assistant","content":"A"},"done":false}""",
                """{"message":{"role":"assistant","content":"B"},"done":false}""",
                """{"done":true}""",
            ).joinToString("\n")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody(ndjson),
        )
        val deltas = mutableListOf<String>()

        sut.chatStream(
            payload =
                OllamaChatRequest(
                    model = MODEL,
                    messages = listOf(OllamaChatMessageRequest("user", "stream no onDone")),
                    options = OllamaChatOptionsRequest(temperature = 0.1),
                ),
            onDelta = { deltas += it },
        )

        val (recorded, json) = captureRequestJson()
        assertThat(recorded.path).isEqualTo("/api/chat")
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(json["stream"].asBoolean()).isTrue
        assertThat(json["keep_alive"].asText()).isEqualTo(props.ollama.keepAlive)
        assertThat(deltas).containsExactly("A", "B")
    }

    @Test
    fun `should stream using response field fallback`() {
        val ndjson =
            listOf(
                """{"response":"X","done":false}""",
                """{"response":"Y","done":false}""",
                """{"done":true}""",
            ).joinToString("\n") + "\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-ndjson")
                .setChunkedBody(ndjson, 8),
        )
        val deltas = mutableListOf<String>()
        var last: OllamaChatStreamChunkResponse? = null

        sut.chatStream(
            payload =
                OllamaChatRequest(
                    model = MODEL,
                    messages = listOf(OllamaChatMessageRequest("user", "fallback test")),
                ),
            onDelta = { deltas += it },
            onDoneChunk = { last = it },
        )

        val (recorded, json) = captureRequestJson()
        assertThat(recorded.path).isEqualTo("/api/chat")
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(json["stream"].asBoolean()).isTrue
        assertThat(deltas).containsExactly("X", "Y")
        val lastChunk = requireNotNull(last) { "onDoneChunk must be called" }
        assertThat(lastChunk.done).isTrue
    }

    @Test
    fun `should encode request body as UTF-8`() {
        val okBody =
            mapper.writeValueAsString(
                OllamaChatResponse(message = OllamaChatResponseMessage("assistant", "ok"), done = true),
            )
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))
        val exotic = "hello 🌎 — coffee"

        sut.chat(
            OllamaChatRequest(
                model = MODEL,
                messages = listOf(OllamaChatMessageRequest("user", exotic)),
            ),
        )

        val (_, json) = captureRequestJson()
        assertThat(json["messages"][0]["content"].asText()).isEqualTo(exotic)
    }

    private fun captureRequestJson(): Pair<RecordedRequest, JsonNode> {
        val recorded = server.takeRequest()
        val json = mapper.readTree(recorded.body.readUtf8())
        return recorded to json
    }
}
