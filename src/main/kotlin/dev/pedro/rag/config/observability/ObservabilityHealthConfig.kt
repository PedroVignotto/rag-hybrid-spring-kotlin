package dev.pedro.rag.config.observability

import dev.pedro.rag.config.llm.LlmProperties
import dev.pedro.rag.infra.llm.ollama.health.OllamaHealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient

@Configuration
class ObservabilityHealthConfig {
    @Bean("ollama")
    fun ollamaHealthIndicator(
        http: HttpClient,
        props: LlmProperties,
    ): OllamaHealthIndicator = OllamaHealthIndicator(http = http, props = props)
}
