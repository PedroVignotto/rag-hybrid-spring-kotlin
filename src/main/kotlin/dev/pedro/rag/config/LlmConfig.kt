package dev.pedro.rag.config

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.api.chat.support.ChatSseBridge
import dev.pedro.rag.application.chat.ChatUseCase
import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.infra.llm.ollama.OllamaChatProvider
import dev.pedro.rag.infra.llm.ollama.client.OllamaClient
import dev.pedro.rag.infra.llm.ollama.support.NdjsonStreamProcessor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient

@Configuration
@EnableConfigurationProperties(LlmProperties::class)
class LlmConfig {
    @Bean
    fun httpClient(props: LlmProperties): HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(props.http.connectTimeout)
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    @Bean
    fun ndjsonStreamProcessor(mapper: ObjectMapper) = NdjsonStreamProcessor(mapper)

    @Bean
    fun ollamaClient(
        http: HttpClient,
        mapper: ObjectMapper,
        props: LlmProperties,
        processor: NdjsonStreamProcessor,
    ) = OllamaClient(
        http = http,
        mapper = mapper,
        properties = props.ollama,
        streamProcessor = processor,
    )

    @Bean
    fun llmChatPort(
        client: OllamaClient,
        props: LlmProperties,
    ): LlmChatPort = OllamaChatProvider(client, defaultModel = props.ollama.model)

    @Bean
    fun chatUseCase(port: LlmChatPort) = ChatUseCase(port)

    @Bean
    fun chatSseBridge(
        useCase: ChatUseCase,
        mapper: ObjectMapper,
    ) = ChatSseBridge(useCase = useCase, mapper = mapper)
}
