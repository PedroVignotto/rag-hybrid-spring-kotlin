package dev.pedro.rag.config.llm

import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.time.DurationMin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI
import java.time.Duration

@ConfigurationProperties("llm")
@Validated
data class LlmProperties(
    val http: Http,
    val ollama: Ollama,
) {
    data class Http(
        @field:DurationMin(seconds = 1)
        val connectTimeout: Duration,
    )

    data class Ollama(
        val baseUrl: URI,
        @field:DurationMin(seconds = 1)
        val requestTimeout: Duration,
        @field:NotBlank
        val model: String,
        val keepAlive: String,
        @field:NotBlank
        val providerTag: String,
    )
}
