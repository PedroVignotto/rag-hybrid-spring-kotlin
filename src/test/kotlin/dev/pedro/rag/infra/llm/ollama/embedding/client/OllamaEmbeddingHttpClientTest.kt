package dev.pedro.rag.infra.llm.ollama.embedding.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.config.llm.LlmProperties
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
import java.util.concurrent.ExecutionException
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
    fun `should POST to api-embed with single input and parse 'embedding'`() {
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

        val out = sut.embed(model = MODEL, inputs = listOf("only-one"))

        val (recorded, json) = captureRequestJson()
        assertThat(recorded.path).isEqualTo("/api/embed")
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.getHeader("Content-Type")).isEqualTo("application/json")
        assertThat(json["model"].asText()).isEqualTo(MODEL)
        assertThat(json["input"].asText()).isEqualTo("only-one")
        assertThat(out).hasSize(1)
        assertThat(out[0].toList()).containsExactly(0.9f, 1.1f, 1.3f)
    }

    @Test
    fun `should accept 'embeddings' matrix when single input`() {
        val body =
            """
            {
              "model": "$MODEL",
              "embeddings": [[0.1, 0.2, 0.3]]
            }
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"),
        )

        val out = sut.embed(model = MODEL, inputs = listOf("one"))

        val (recorded, json) = captureRequestJson()
        assertThat(recorded.path).isEqualTo("/api/embed")
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(json["model"].asText()).isEqualTo(MODEL)
        assertThat(json["input"].asText()).isEqualTo("one")
        assertThat(out).hasSize(1)
        assertThat(out[0].toList()).containsExactly(0.1f, 0.2f, 0.3f)
    }

    @Test
    fun `should POST one request per input and return both vectors (order preserved, any arrival order)`() {
        val inputs = listOf("hello", "world")
        val resp1 =
            """
            { "model": "$MODEL", "embedding": [0.1, 0.2, 0.3] }
            """.trimIndent()
        val resp2 =
            """
            { "model": "$MODEL", "embedding": [0.4, 0.5, 0.6] }
            """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(resp1).addHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(resp2).addHeader("Content-Type", "application/json"))

        val out = sut.embed(model = MODEL, inputs = inputs)

        val (req1, json1) = captureRequestJson()
        val (req2, json2) = captureRequestJson()
        assertThat(listOf(req1.path, req2.path)).allMatch { it == "/api/embed" }
        assertThat(listOf(req1.method, req2.method)).allMatch { it == "POST" }
        val sentInputs = setOf(json1["input"].asText(), json2["input"].asText())
        assertThat(sentInputs).isEqualTo(inputs.toSet())
        val expected =
            listOf(
                listOf(0.1f, 0.2f, 0.3f),
                listOf(0.4f, 0.5f, 0.6f),
            )
        val actual = out.map { it.toList() }
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected)
    }

    @Test
    fun `should process inputs in windows and preserve order for N  MAX_PARALLEL_EMBEDS`() =
        withServerDispatcher(handler = { req ->
            val json = mapper.readTree(req.body.readUtf8())
            val input = json["input"].asText()
            val i = input.removePrefix("t").toInt()
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{ "model":"$MODEL", "embedding":[ $i, ${i + 0.5} ] }""")
        }) {
            val inputs = (0 until 20).map { "t$it" }

            val out = sut.embed(model = MODEL, inputs = inputs)

            assertThat(out).hasSize(inputs.size)
            out.forEachIndexed { i, vec ->
                assertThat(vec.toList()).containsExactly(i.toFloat(), i.toFloat() + 0.5f)
            }
        }

    @Test
    fun `should fail fast in window when one embed returns non-2xx`() =
        withServerDispatcher(handler = { req ->
            val json = mapper.readTree(req.body.readUtf8())
            val input = json["input"].asText() // ex.: "t7"
            if (input == "t7") {
                MockResponse().setResponseCode(500).setBody("boom")
            } else {
                val i = input.removePrefix("t").toInt()
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""{ "model":"$MODEL", "embedding":[ $i, ${i + 0.5} ] }""")
            }
        }) {
            val inputs = (0 until 20).map { "t$it" }

            val exception = assertFailsWith<ExecutionException> { sut.embed(model = MODEL, inputs = inputs) }
            assertThat(exception.cause).isInstanceOf(OllamaHttpException::class.java)
            val cause = exception.cause as OllamaHttpException
            assertThat(cause.status).isEqualTo(500)
            assertThat(cause.responseBody).contains("boom")
        }

    @Test
    fun `should encode request body as UTF-8`() {
        val exotic = "olá 🌎 — café"
        val body =
            """
            {
              "model": "$MODEL",
              "embedding": [1.0, 2.0]
            }
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"),
        )

        sut.embed(model = MODEL, inputs = listOf(exotic))

        val (_, json) = captureRequestJson()
        assertThat(json["input"].asText()).isEqualTo(exotic)
    }

    @Test
    fun `should throw OllamaHttpException on non-2xx`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val exception = assertFailsWith<OllamaHttpException> { sut.embed(model = MODEL, inputs = listOf("x")) }
        assertThat(exception.status).isEqualTo(500)
        assertThat(exception.responseBody).contains("boom")
        server.takeRequest()
    }

    @Test
    fun `should throw InvalidResponse on empty or invalid json`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val exception = assertFailsWith<OllamaInvalidResponseException> { sut.embed(model = MODEL, inputs = listOf("x")) }
        assertThat(exception.message).contains("Invalid JSON")
    }

    @Test
    fun `should surface Ollama error from response`() {
        val body = """{ "error": "model 'mxbai-embed-large' not found" }"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"),
        )

        val exception = assertFailsWith<OllamaInvalidResponseException> { sut.embed(model = MODEL, inputs = listOf("x")) }
        assertThat(exception.message).contains("Ollama error: model 'mxbai-embed-large' not found")
        val (recorded, json) = captureRequestJson()
        assertThat(recorded.path).isEqualTo("/api/embed")
        assertThat(json["model"].asText()).isEqualTo(MODEL)
    }

    @Test
    fun `should throw InvalidResponse when 'embedding' and 'embeddings' are missing`() {
        val body = """{ "model": "$MODEL" }"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"),
        )

        val exception = assertFailsWith<OllamaInvalidResponseException> { sut.embed(model = MODEL, inputs = listOf("x")) }
        assertThat(exception.message).contains("missing 'embedding' or 'embeddings'")
    }

    @Test
    fun `should throw InvalidResponse on empty embedding vector`() {
        val body =
            """
            {
              "model": "$MODEL",
              "embedding": []
            }
            """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"),
        )

        val exception = assertFailsWith<OllamaInvalidResponseException> { sut.embed(model = MODEL, inputs = listOf("x")) }
        assertThat(exception.message).contains("empty embedding vector")
    }

    @Test
    fun `should validate inputs not empty`() {
        val exception = assertThrows<IllegalArgumentException> { sut.embed(model = MODEL, inputs = emptyList()) }
        assertThat(exception).hasMessage("inputs must not be empty")
    }

    @Test
    fun `should validate inputs not blank`() {
        val exception = assertThrows<IllegalArgumentException> { sut.embed(model = MODEL, inputs = listOf("ok", " ")) }
        assertThat(exception).hasMessageContaining("must not contain blank")
    }

    private fun captureRequestJson(): Pair<RecordedRequest, JsonNode> {
        val recorded = server.takeRequest()
        val json = mapper.readTree(recorded.body.readUtf8())
        return recorded to json
    }

    private inline fun withServerDispatcher(
        crossinline handler: (RecordedRequest) -> MockResponse,
        block: () -> Unit,
    ) {
        val prev = server.dispatcher
        server.dispatcher =
            object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = handler(request)
            }
        try {
            block()
        } finally {
            server.dispatcher = prev
        }
    }
}
