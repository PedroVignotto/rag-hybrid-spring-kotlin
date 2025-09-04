package dev.pedro.rag.infra.llm.ollama.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.config.LlmProperties
import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatOptions
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequestMessage
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatResponse
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatResponseMessage
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

private const val MODEL = "llama3.2:3b"

@SpringBootTest
@ActiveProfiles("test")
class OllamaClientTest {
    companion object {
        private val server = MockWebServer()

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
                    messages = listOf(OllamaChatRequestMessage("user", "hi")),
                    options = OllamaChatOptions(temperature = 0.2, topP = 0.9, numPredict = 32),
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
    }

    @Test
    fun `should throw OllamaHttpException on non-2xx`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val response =
            assertFailsWith<OllamaHttpException> {
                sut.chat(
                    OllamaChatRequest(
                        model = MODEL,
                        messages = listOf(OllamaChatRequestMessage("user", "x")),
                    ),
                )
            }
        assertThat(response.status).isEqualTo(500)
        assertThat(response.responseBody).contains("boom")
    }

    @Test
    fun `should throw when response missing message content`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"message":{}}"""))

        val response =
            assertFailsWith<OllamaInvalidResponseException> {
                sut.chat(
                    OllamaChatRequest(
                        model = MODEL,
                        messages = listOf(OllamaChatRequestMessage("user", "x")),
                    ),
                )
            }
        assertThat(response).hasMessage("Ollama response is missing `message.content`")
    }

    private fun captureRequestJson(): Pair<RecordedRequest, JsonNode> {
        val recorded = server.takeRequest()
        val json = mapper.readTree(recorded.body.readUtf8())
        return recorded to json
    }
}
