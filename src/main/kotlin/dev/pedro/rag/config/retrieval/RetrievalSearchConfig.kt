package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.search.usecase.DefaultSearchUseCase
import dev.pedro.rag.application.retrieval.search.usecase.SearchUseCase
import dev.pedro.rag.infra.retrieval.metrics.MetricsSearchUseCase
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class RetrievalSearchConfig {
    @Bean("searchUseCaseCore")
    fun searchUseCaseCore(
        embedPort: EmbedPort,
        vectorStorePort: VectorStorePort,
    ): SearchUseCase =
        DefaultSearchUseCase(
            embedPort = embedPort,
            vectorStorePort = vectorStorePort,
        )

    @Bean
    @Primary
    fun searchUseCase(
        @Qualifier("searchUseCaseCore") core: SearchUseCase,
        metrics: RetrievalMetrics,
        embedPort: EmbedPort,
    ): SearchUseCase =
        MetricsSearchUseCase(
            delegate = core,
            metrics = metrics,
            embedPort = embedPort,
        )
}
