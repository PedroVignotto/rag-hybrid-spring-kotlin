package dev.pedro.rag.infra.llm.ollama.embedding.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.config.llm.LlmProperties
import dev.pedro.rag.infra.llm.ollama.embedding.request.OllamaEmbeddingRequest
import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

@SpringBootTest
@ActiveProfiles("test")
class OllamaEmbeddingHttpClientTest {
    companion object {
        private val server = MockWebServer()
        private const val MODEL = "mxbai-embed-large"

        @JvmStatic
        @BeforeAll
        fun start() {
            server.start()
        }

        @JvmStatic
        @AfterAll
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

    @Autowired lateinit var sut: OllamaEmbeddingHttpClient

    @BeforeEach
    fun drainQueue() {
        while (true) {
            server.takeRequest(10, TimeUnit.MILLISECONDS) ?: break
        }
    }

    @Test
    fun `should POST to api-embeddings and return batch vectors`() {
        val inputs = listOf("hello", "world")
        val body =
            """
            {
              "model": "$MODEL",
              "embeddings": [
                [0.1, 0.2, 0.3],
                [0.4, 0.5, 0.6]
              ]
            }
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"),
        )

        val response = sut.embed(model = MODEL, inputs = inputs)

        val (recorded, json) = captureRequestJson()
        assertThat(recorded.path).isEqualTo("/api/embeddings")
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.getHeader("Content-Type")).isEqualTo("application/json")
        val sent = mapper.treeToValue(json, OllamaEmbeddingRequest::class.java)
        assertThat(sent.model).isEqualTo(MODEL)
        assertThat(sent.input).isEqualTo(inputs)
        assertThat(response).hasSize(2)
        assertThat(response[0].toList()).containsExactly(0.1f, 0.2f, 0.3f)
        assertThat(response[1].toList()).containsExactly(0.4f, 0.5f, 0.6f)
    }

    @Test
    fun `should handle single embedding field when batch size is 1`() {
        val inputs = listOf("only-one")
        val body =
            """
            {
              "model": "$MODEL",
              "embedding": [0.9, 1.1, 1.3]
            }
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"),
        )

        val response = sut.embed(model = MODEL, inputs = inputs)

        val (recorded, json) = captureRequestJson()
        assertThat(recorded.path).isEqualTo("/api/embeddings")
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(json["model"].asText()).isEqualTo(MODEL)
        assertThat(response).hasSize(1)
        assertThat(response[0].toList()).containsExactly(0.9f, 1.1f, 1.3f)
    }

    @Test
    fun `should throw OllamaHttpException on non-2xx`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val response = assertFailsWith<OllamaHttpException> { sut.embed(model = MODEL, inputs = listOf("x")) }
        assertThat(response.status).isEqualTo(500)
        assertThat(response.responseBody).contains("boom")
        server.takeRequest()
    }

    @Test
    fun `should throw InvalidResponse on empty or invalid json`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val response = assertFailsWith<OllamaInvalidResponseException> { sut.embed(model = MODEL, inputs = listOf("x")) }
        assertThat(response.message).contains("Invalid JSON")
    }

    @Test
    fun `should throw InvalidResponse when 'embeddings' or 'embedding' missing`() {
        val body = """{ "model": "$MODEL" }"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"),
        )

        val response = assertFailsWith<OllamaInvalidResponseException> { sut.embed(model = MODEL, inputs = listOf("x")) }
        assertThat(response.message).contains("missing 'embeddings' or 'embedding'")
    }

    @Test
    fun `should throw InvalidResponse on batch size mismatch`() {
        val inputs = listOf("a", "b")
        val body =
            """
            {
              "model": "$MODEL",
              "embeddings": [
                [0.1, 0.2, 0.3]
              ]
            }
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"),
        )

        val response = assertFailsWith<OllamaInvalidResponseException> { sut.embed(model = MODEL, inputs = inputs) }
        assertThat(response.message).contains("size mismatch")
    }

    @Test
    fun `should validate inputs not empty`() {
        val response = assertThrows<IllegalArgumentException> { sut.embed(model = MODEL, inputs = emptyList()) }
        assertThat(response).hasMessage("inputs must not be empty")
    }

    @Test
    fun `should validate inputs not blank`() {
        val response = assertThrows<IllegalArgumentException> { sut.embed(model = MODEL, inputs = listOf("ok", " ")) }
        assertThat(response).hasMessageContaining("must not contain blank")
    }

    private fun captureRequestJson(): Pair<RecordedRequest, JsonNode> {
        val recorded = server.takeRequest()
        val json = mapper.readTree(recorded.body.readUtf8())
        return recorded to json
    }
}
