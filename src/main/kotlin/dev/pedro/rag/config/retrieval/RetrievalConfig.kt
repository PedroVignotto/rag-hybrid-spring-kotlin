package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.delete.usecase.DefaultDeleteUseCase
import dev.pedro.rag.application.retrieval.delete.usecase.DeleteUseCase
import dev.pedro.rag.application.retrieval.ingest.usecase.DefaultIngestUseCase
import dev.pedro.rag.application.retrieval.ingest.usecase.IngestUseCase
import dev.pedro.rag.application.retrieval.ports.Chunker
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.search.usecase.DefaultSearchUseCase
import dev.pedro.rag.application.retrieval.search.usecase.SearchUseCase
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.infra.retrieval.chunker.SimpleChunker
import dev.pedro.rag.infra.retrieval.metrics.MetricsIngestUseCase
import dev.pedro.rag.infra.retrieval.metrics.MetricsSearchUseCase
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics
import dev.pedro.rag.infra.retrieval.vectorstore.memory.InMemoryVectorStore
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class RetrievalConfig {
    @Bean
    fun chunker(): Chunker = SimpleChunker()

    @Bean
    fun vectorStorePort(): VectorStorePort = InMemoryVectorStore()

    @Bean
    fun retrievalMetrics(registry: MeterRegistry): RetrievalMetrics = RetrievalMetrics(registry)

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

    @Bean("deleteUseCaseCore")
    fun deleteUseCaseCore(
        vectorStorePort: VectorStorePort,
        collectionSpec: CollectionSpec,
    ): DeleteUseCase =
        DefaultDeleteUseCase(
            vectorStore = vectorStorePort,
            activeCollection = collectionSpec,
        )

    @Bean
    @Primary
    fun deleteUseCase(
        @Qualifier("deleteUseCaseCore") core: DeleteUseCase,
    ): DeleteUseCase = core
}
