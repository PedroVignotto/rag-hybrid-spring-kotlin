package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.infra.retrieval.embedding.decorator.NormalizingEmbedPort
import dev.pedro.rag.infra.retrieval.embedding.fake.FakeEmbeddingProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RetrievalProperties::class)
class RetrievalEmbeddingConfig(private val props: RetrievalProperties) {
    @Bean
    fun embeddingSpec(props: RetrievalProperties): EmbeddingSpec =
        EmbeddingSpec(
            provider = props.embedding.provider,
            model = props.embedding.model,
            dim = props.embedding.dimension,
            normalized = props.embedding.normalized,
        )

    @Bean
    fun collectionSpec(embeddingSpec: EmbeddingSpec): CollectionSpec = CollectionSpec.fromSpec(embeddingSpec)

    @Bean
    fun embedPort(): EmbedPort {
        val spec =
            EmbeddingSpec(
                provider = props.embedding.provider,
                model = props.embedding.model,
                dim = props.embedding.dimension,
                normalized = props.embedding.normalized,
            )
        return NormalizingEmbedPort(FakeEmbeddingProvider(spec))
    }
}
