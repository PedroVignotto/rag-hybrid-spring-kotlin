package dev.pedro.rag.infra.retrieval.embed.fake

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.EmbedPortContractTest
import dev.pedro.rag.domain.retrieval.EmbeddingSpec

class FakeEmbeddingProviderTest : EmbedPortContractTest() {

    override fun sutFor(normalized: Boolean): EmbedPort =
        FakeEmbeddingProvider(
            embeddingSpec = EmbeddingSpec(
                provider = "fake",
                model = "fake-v1",
                dim = 16,
                normalized = normalized
            )
        )
}