package dev.pedro.rag.infra.retrieval.embed

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.ports.EmbedPortContractTest
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.infra.retrieval.embed.decorator.NormalizingEmbedPort
import dev.pedro.rag.infra.retrieval.embed.fake.FakeEmbeddingProvider

class EmbeddingPipelineTest : EmbedPortContractTest() {
    override fun sutFor(normalized: Boolean): EmbedPort {
        val spec =
            EmbeddingSpec(
                provider = "fake",
                model = "fake-v1",
                dim = 16,
                normalized = normalized,
            )
        val base = FakeEmbeddingProvider(spec)
        return NormalizingEmbedPort(base)
    }
}
