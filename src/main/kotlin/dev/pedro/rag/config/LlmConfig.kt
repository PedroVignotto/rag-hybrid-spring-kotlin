package dev.pedro.rag.config

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.infra.llm.ollama.client.OllamaClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient

@Configuration
@EnableConfigurationProperties(LlmProperties::class)
class LlmConfig {

    @Bean
    fun httpClient(props: LlmProperties): HttpClient =
        HttpClient.newBuilder().connectTimeout(props.http.connectTimeout).build()

    @Bean
    fun ollamaClient(http: HttpClient, mapper: ObjectMapper, props: LlmProperties) =
        OllamaClient(http, mapper, props.ollama)
}