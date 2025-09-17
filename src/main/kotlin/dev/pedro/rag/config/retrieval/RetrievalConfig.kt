package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.ingest.usecase.DefaultIngestUseCase
import dev.pedro.rag.application.retrieval.ingest.usecase.IngestUseCase
import dev.pedro.rag.application.retrieval.ports.Chunker
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.search.usecase.DefaultSearchUseCase
import dev.pedro.rag.application.retrieval.search.usecase.SearchUseCase
import dev.pedro.rag.infra.retrieval.chunker.SimpleChunker
import dev.pedro.rag.infra.retrieval.vectorstore.memory.InMemoryVectorStore
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
    ): IngestUseCase = core

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
    ): SearchUseCase = core
}
