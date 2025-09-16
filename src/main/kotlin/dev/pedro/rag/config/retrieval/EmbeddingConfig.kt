package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.infra.retrieval.embed.decorator.NormalizingEmbedPort
import dev.pedro.rag.infra.retrieval.embed.fake.FakeEmbeddingProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RetrievalProperties::class)
class EmbeddingConfig(private val props: RetrievalProperties) {
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
