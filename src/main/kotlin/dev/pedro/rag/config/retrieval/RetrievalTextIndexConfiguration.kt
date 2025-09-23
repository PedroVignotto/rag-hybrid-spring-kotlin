package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.retrieval.ports.TextIndexPort
import dev.pedro.rag.infra.retrieval.textindex.bm25.InMemoryTextIndexStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RetrievalTextIndexConfiguration {
    @Bean
    fun textIndexPort(props: RetrievalSearchProperties): TextIndexPort {
        return InMemoryTextIndexStore(
            stopWordsEnabled = props.bm25.stopWordsEnabled,
            stopWords = props.bm25.stopWords,
            bm25TermFrequencySaturation = props.bm25.termFrequencySaturation,
            bm25LengthNormalization = props.bm25.lengthNormalization,
        )
    }
}
