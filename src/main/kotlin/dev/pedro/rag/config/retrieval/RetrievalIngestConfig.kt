package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.ingest.usecase.DefaultIngestUseCase
import dev.pedro.rag.application.retrieval.ingest.usecase.IngestUseCase
import dev.pedro.rag.application.retrieval.ports.Chunker
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.infra.retrieval.metrics.MetricsIngestUseCase
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class RetrievalIngestConfig {
    @Bean("ingestUseCaseCore")
    fun ingestUseCaseCore(
        chunker: Chunker,
        embedPort: EmbedPort,
        vectorStorePort: VectorStorePort,
    ): IngestUseCase =
        DefaultIngestUseCase(
            chunker = chunker,
            embedPort = embedPort,
            vectorStorePort = vectorStorePort,
        )

    @Bean
    @Primary
    fun ingestUseCase(
        @Qualifier("ingestUseCaseCore") core: IngestUseCase,
        metrics: RetrievalMetrics,
        embedPort: EmbedPort,
    ): IngestUseCase =
        MetricsIngestUseCase(
            delegate = core,
            metrics = metrics,
            embedPort = embedPort,
        )
}
