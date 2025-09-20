package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.infra.llm.ollama.embedding.client.OllamaEmbeddingHttpClient
import dev.pedro.rag.infra.retrieval.embedding.decorator.NormalizingEmbedPort
import dev.pedro.rag.infra.retrieval.embedding.fake.FakeEmbeddingProvider
import dev.pedro.rag.infra.retrieval.embedding.ollama.OllamaEmbeddingProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableConfigurationProperties(RetrievalProperties::class)
class RetrievalEmbeddingConfig(private val props: RetrievalProperties) {
    @Bean
    fun embeddingSpec(): EmbeddingSpec =
        EmbeddingSpec(
            provider = props.embedding.provider,
            model = props.embedding.model,
            dim = props.embedding.dimension,
            normalized = props.embedding.normalized,
        )

    @Bean
    fun collectionSpec(embeddingSpec: EmbeddingSpec): CollectionSpec = CollectionSpec.fromSpec(embeddingSpec)

    @Bean("rawEmbedPort")
    @ConditionalOnProperty(prefix = "retrieval.embedding", name = ["provider"], havingValue = "ollama")
    fun ollamaRaw(
        client: OllamaEmbeddingHttpClient,
        spec: EmbeddingSpec,
    ): EmbedPort = OllamaEmbeddingProvider(client, spec)

    @Bean("rawEmbedPort")
    @ConditionalOnProperty(prefix = "retrieval.embedding", name = ["provider"], havingValue = "fake")
    fun fakeRaw(spec: EmbeddingSpec): EmbedPort = FakeEmbeddingProvider(spec)

    @Bean
    @Primary
    @ConditionalOnBean(name = ["rawEmbedPort"])
    @ConditionalOnProperty(prefix = "retrieval.embedding", name = ["normalized"], havingValue = "true")
    fun embedPortNormalized(
        @Qualifier("rawEmbedPort") base: EmbedPort,
    ): EmbedPort = NormalizingEmbedPort(base)
}
