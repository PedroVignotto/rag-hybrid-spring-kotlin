package dev.pedro.rag.infra.retrieval.textindex.bm25

import dev.pedro.rag.application.retrieval.ports.TextIndexPort
import dev.pedro.rag.application.retrieval.ports.TextIndexPortContractTest

class InMemoryTextIndexStoreContractTest : TextIndexPortContractTest() {
    override fun newSut(): TextIndexPort =
        InMemoryTextIndexStore(
            stopWordsEnabled = false,
            stopWords = emptySet(),
            bm25TermFrequencySaturation = 1.2,
            bm25LengthNormalization = 0.75,
        )
}
