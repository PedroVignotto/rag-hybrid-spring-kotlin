package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.TextIndexPort
import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.application.retrieval.search.ranking.HybridSearchAggregator
import dev.pedro.rag.application.retrieval.search.usecase.DefaultSearchUseCase
import dev.pedro.rag.application.retrieval.search.usecase.SearchUseCase
import dev.pedro.rag.infra.retrieval.metrics.MetricsSearchUseCase
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics
import dev.pedro.rag.infra.retrieval.textindex.bm25.InMemoryTextIndexStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class RetrievalSearchConfig {
    @Bean
    fun textIndexPort(props: RetrievalSearchProperties): TextIndexPort {
        return InMemoryTextIndexStore(
            stopWordsEnabled = props.bm25.stopWordsEnabled,
            stopWords = props.bm25.stopWords,
            bm25TermFrequencySaturation = props.bm25.termFrequencySaturation,
            bm25LengthNormalization = props.bm25.lengthNormalization,
        )
    }

    @Bean
    fun hybridSearchAggregator(props: RetrievalSearchProperties): HybridSearchAggregator =
        HybridSearchAggregator(alpha = props.fusion.alpha)

    @Bean("searchUseCaseCore")
    fun searchUseCaseCore(
        embedPort: EmbedPort,
        vectorStorePort: VectorStorePort,
        textIndexPort: TextIndexPort,
        props: RetrievalSearchProperties,
        aggregator: HybridSearchAggregator,
    ): SearchUseCase =
        DefaultSearchUseCase(
            embedPort = embedPort,
            vectorStorePort = vectorStorePort,
            textIndexPort = textIndexPort,
            props = props,
            aggregator = aggregator,
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
