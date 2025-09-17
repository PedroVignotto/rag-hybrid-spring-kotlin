package dev.pedro.rag.infra.retrieval.metrics

import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.search.dto.SearchInput
import dev.pedro.rag.application.retrieval.search.dto.SearchOutput
import dev.pedro.rag.application.retrieval.search.usecase.SearchUseCase
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_ERROR
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_SUCCESS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.OP_SEARCH

class MetricsSearchUseCase(
    private val delegate: SearchUseCase,
    private val metrics: RetrievalMetrics,
    private val embedPort: EmbedPort,
) : SearchUseCase {
    override fun search(input: SearchInput): SearchOutput {
        val spec = embedPort.spec()

        val start = System.nanoTime()
        var status = STATUS_SUCCESS
        try {
            val out = delegate.search(input)
            metrics.recordK(
                provider = spec.provider,
                model = spec.model,
                dim = spec.dim,
                normalized = spec.normalized,
                k = input.topK,
            )
            metrics.recordHits(
                provider = spec.provider,
                model = spec.model,
                dim = spec.dim,
                normalized = spec.normalized,
                hits = out.matches.size,
            )
            return out
        } catch (ex: Exception) {
            status = STATUS_ERROR
            metrics.countError(
                provider = spec.provider,
                model = spec.model,
                dim = spec.dim,
                normalized = spec.normalized,
                type = ex::class.simpleName ?: "Unknown",
                stage = OP_SEARCH,
                upstreamStatus = null,
            )
            throw ex
        } finally {
            metrics.recordLatency(
                op = OP_SEARCH,
                provider = spec.provider,
                model = spec.model,
                dim = spec.dim,
                normalized = spec.normalized,
                status = status,
                nanos = System.nanoTime() - start,
            )
        }
    }
}
