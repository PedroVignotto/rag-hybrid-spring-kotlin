package dev.pedro.rag.infra.llm.ollama.health

import dev.pedro.rag.config.LlmProperties
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

class OllamaHealthIndicatorTest {
    private lateinit var server: MockWebServer
    private lateinit var sut: OllamaHealthIndicator

    @BeforeEach
    fun setUp() {
        server = MockWebServer().also { it.start() }
        sut = OllamaHealthIndicator(http(), props(baseUrlFrom(server)))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `should report UP when tags endpoint returns 200`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"models":[]}"""))

        val health = sut.health()

        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details["provider"]).isEqualTo("ollama")
        assertThat(health.details["model"]).isEqualTo("llama3.2:3b")
        assertThat(health.details["endpoint"]).isEqualTo("/api/tags")
    }

    @Test
    fun `should report DOWN with status when tags endpoint returns 500`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val health = sut.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details["provider"]).isEqualTo("ollama")
        assertThat(health.details["endpoint"]).isEqualTo("/api/tags")
        assertThat(health.details["status"]).isEqualTo(500)
    }

    @Test
    fun `should report DOWN when connection is interrupted (exception branch)`() {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
        )

        val health = sut.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details["provider"]).isEqualTo("ollama")
        assertThat(health.details["endpoint"]).isEqualTo("/api/tags")
        assertThat(health.details.containsKey("status")).isFalse()
    }

    private fun http(): HttpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(1))
            .build()

    private fun baseUrlFrom(server: MockWebServer): URI = URI.create(server.url("/").toString())

    private fun props(baseUrl: URI) =
        LlmProperties(
            http = LlmProperties.Http(connectTimeout = Duration.ofSeconds(1)),
            ollama =
                LlmProperties.Ollama(
                    baseUrl = baseUrl,
                    requestTimeout = Duration.ofSeconds(1),
                    model = "llama3.2:3b",
                    keepAlive = "0s",
                    providerTag = "ollama",
                ),
        )
}
