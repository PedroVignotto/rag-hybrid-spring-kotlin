package dev.pedro.rag.infra.llm.ollama.health

import dev.pedro.rag.config.llm.LlmProperties
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OllamaHealthIndicator(
    private val http: HttpClient,
    private val props: LlmProperties,
) : HealthIndicator {
    private companion object {
        const val TAGS_PATH: String = "/api/tags"
    }

    private val readinessTimeout: Duration = Duration.ofSeconds(2)

    override fun health(): Health {
        val uri = buildTagsEndpointUri(props.ollama.baseUrl)
        val request = buildReadinessRequest(uri, readinessTimeout)

        return runCatching { sendReadinessRequest(request).statusCode() }
            .fold(
                onSuccess = { code ->
                    if (isSuccessful(code)) {
                        buildUpHealth()
                    } else {
                        buildDownHealth(statusCode = code)
                    }
                },
                onFailure = { t ->
                    buildDownHealth(exception = t)
                },
            )
    }

    private fun buildTagsEndpointUri(base: URI): URI = joinUri(base)

    private fun buildReadinessRequest(
        uri: URI,
        timeout: Duration,
    ): HttpRequest =
        HttpRequest.newBuilder()
            .uri(uri)
            .GET()
            .timeout(timeout)
            .build()

    private fun sendReadinessRequest(request: HttpRequest): HttpResponse<Void> = http.send(request, HttpResponse.BodyHandlers.discarding())

    private fun isSuccessful(code: Int) = code in 200..299

    private fun buildUpHealth(): Health =
        Health.up()
            .withProviderDetails()
            .withDetail("endpoint", TAGS_PATH)
            .build()

    private fun buildDownHealth(
        statusCode: Int? = null,
        exception: Throwable? = null,
    ): Health {
        val builder = if (exception != null) Health.down(exception) else Health.down()
        statusCode?.let { builder.withDetail("status", it) }
        return builder
            .withProviderDetails()
            .withDetail("endpoint", TAGS_PATH)
            .build()
    }

    private fun Health.Builder.withProviderDetails(): Health.Builder =
        this.withDetail("provider", props.ollama.providerTag)
            .withDetail("model", props.ollama.model)

    private fun joinUri(base: URI): URI {
        val b = base.toString().trimEnd('/')
        val p = TAGS_PATH.trimStart('/')
        return URI.create("$b/$p")
    }
}
