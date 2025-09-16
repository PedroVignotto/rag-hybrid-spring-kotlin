package dev.pedro.rag.config.llm

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.api.chat.support.ChatSseBridge
import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.application.chat.usecase.ChatUseCase
import dev.pedro.rag.application.chat.usecase.DefaultChatUseCase
import dev.pedro.rag.infra.llm.metrics.LlmMetrics
import dev.pedro.rag.infra.llm.metrics.MetricsLlmChatPort
import dev.pedro.rag.infra.llm.ollama.OllamaChatProvider
import dev.pedro.rag.infra.llm.ollama.client.OllamaClient
import dev.pedro.rag.infra.llm.ollama.support.NdjsonStreamProcessor
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
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
    fun llmMetrics(registry: MeterRegistry) = LlmMetrics(registry)

    @Bean("ollamaProvider")
    fun ollamaProvider(
        client: OllamaClient,
        props: LlmProperties,
    ): LlmChatPort =
        OllamaChatProvider(
            client = client,
            defaultModel = props.ollama.model,
        )

    @Bean
    @Primary
    fun llmChatPort(
        @Qualifier("ollamaProvider") delegate: LlmChatPort,
        metrics: LlmMetrics,
        props: LlmProperties,
    ): LlmChatPort =
        MetricsLlmChatPort(
            delegate = delegate,
            metrics = metrics,
            providerTag = props.ollama.providerTag,
            modelTag = props.ollama.model,
        )

    @Bean("chatUseCaseCore")
    fun chatUseCaseCore(port: LlmChatPort): ChatUseCase = DefaultChatUseCase(port)

    @Bean
    @Primary
    fun chatUseCase(
        @Qualifier("chatUseCaseCore") core: ChatUseCase,
    ): ChatUseCase = core

    @Bean
    fun chatSseBridge(
        useCase: ChatUseCase,
        mapper: ObjectMapper,
    ) = ChatSseBridge(useCase = useCase, mapper = mapper)
}
