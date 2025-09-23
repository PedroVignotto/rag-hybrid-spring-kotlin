package dev.pedro.rag.config.llm

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.api.chat.support.ChatSseBridge
import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.application.chat.usecase.ChatUseCase
import dev.pedro.rag.application.chat.usecase.DefaultChatUseCase
import dev.pedro.rag.infra.llm.metrics.LlmMetrics
import dev.pedro.rag.infra.llm.metrics.MetricsLlmChatPort
import dev.pedro.rag.infra.llm.ollama.chat.client.OllamaChatHttpClient
import dev.pedro.rag.infra.llm.ollama.chat.provider.OllamaChatProvider
import dev.pedro.rag.infra.llm.ollama.embedding.client.OllamaEmbeddingHttpClient
import dev.pedro.rag.infra.llm.ollama.support.NdjsonStreamProcessor
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.net.http.HttpClient

@Configuration
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
    fun ollamaChatHttpClient(
        http: HttpClient,
        mapper: ObjectMapper,
        props: LlmProperties,
        processor: NdjsonStreamProcessor,
    ) = OllamaChatHttpClient(
        http = http,
        mapper = mapper,
        properties = props.ollama,
        streamProcessor = processor,
    )

    @Bean
    fun llmMetrics(registry: MeterRegistry) = LlmMetrics(registry)

    @Bean("ollamaProvider")
    fun ollamaProvider(
        client: OllamaChatHttpClient,
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

    @Bean
    fun ollamaEmbeddingHttpClient(
        http: HttpClient,
        mapper: ObjectMapper,
        props: LlmProperties,
    ) = OllamaEmbeddingHttpClient(
        http = http,
        mapper = mapper,
        properties = props.ollama,
    )
}
