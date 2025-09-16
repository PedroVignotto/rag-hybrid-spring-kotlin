package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.ports.Chunker
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.usecase.IngestUseCase
import dev.pedro.rag.application.retrieval.usecase.SearchUseCase
import dev.pedro.rag.infra.retrieval.chunker.SimpleChunker
import dev.pedro.rag.infra.retrieval.vectorstore.memory.InMemoryVectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RetrievalConfig {
    @Bean
    fun chunker(): Chunker = SimpleChunker()

    @Bean
    fun vectorStorePort(): VectorStorePort = InMemoryVectorStore()

    @Bean
    fun ingestUseCase(
        chunker: Chunker,
        embedPort: EmbedPort,
        vectorStorePort: VectorStorePort,
    ): IngestUseCase =
        IngestUseCase(
            chunker = chunker,
            embedPort = embedPort,
            vectorStorePort = vectorStorePort,
        )

    @Bean
    fun searchUseCase(
        embedPort: EmbedPort,
        vectorStorePort: VectorStorePort,
    ): SearchUseCase =
        SearchUseCase(
            embedPort = embedPort,
            vectorStorePort = vectorStorePort,
        )
}
