package dev.pedro.rag.infra.retrieval.embedding.ollama

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.infra.llm.ollama.embedding.client.OllamaEmbeddingHttpClient

class OllamaEmbeddingProvider(
    private val client: OllamaEmbeddingHttpClient,
    private val embeddingSpec: EmbeddingSpec,
) : EmbedPort {
    override fun spec(): EmbeddingSpec = embeddingSpec

    override fun embed(text: String): EmbeddingVector = embedAll(listOf(text)).first()

    override fun embedAll(texts: List<String>): List<EmbeddingVector> {
        if (texts.isEmpty()) return emptyList()
        val batches = client.embed(model = embeddingSpec.model, inputs = texts)
        return batches.map { values ->
            validateDimension(values.size, embeddingSpec.dim)
            EmbeddingVector(values = values, dim = embeddingSpec.dim, normalized = false)
        }
    }

    private fun validateDimension(
        actual: Int,
        expected: Int,
    ) {
        require(actual == expected) {
            "Embedding dimension mismatch: expected $expected, got $actual"
        }
    }
}
