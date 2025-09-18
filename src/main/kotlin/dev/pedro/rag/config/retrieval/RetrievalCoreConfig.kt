package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.ports.Chunker
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.infra.retrieval.chunker.SimpleChunker
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics
import dev.pedro.rag.infra.retrieval.vectorstore.memory.InMemoryVectorStore
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RetrievalCoreConfig {
    @Bean
    fun chunker(): Chunker = SimpleChunker()

    @Bean
    fun vectorStorePort(): VectorStorePort = InMemoryVectorStore()

    @Bean
    fun retrievalMetrics(registry: MeterRegistry): RetrievalMetrics = RetrievalMetrics(registry)
}
