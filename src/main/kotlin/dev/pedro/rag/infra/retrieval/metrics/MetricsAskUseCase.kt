package dev.pedro.rag.infra.retrieval.metrics

import dev.pedro.rag.application.retrieval.ask.dto.AskInput
import dev.pedro.rag.application.retrieval.ask.dto.AskOutput
import dev.pedro.rag.application.retrieval.ask.usecase.AskUseCase
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_ERROR
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_SUCCESS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.OP_ASK

class MetricsAskUseCase(
    private val delegate: AskUseCase,
    private val metrics: RetrievalMetrics,
    private val embedPort: EmbedPort,
) : AskUseCase {

    override fun handle(input: AskInput): AskOutput {
        val spec = embedPort.spec()
        val start = System.nanoTime()
        var status = STATUS_SUCCESS

        try {
            val out = delegate.handle(input)
             metrics.recordK(
                 provider = spec.provider,
                 model = spec.model,
                 dim = spec.dim,
                 normalized = spec.normalized,
                 k = out.usedK,
             )
             metrics.recordHits(
                 provider = spec.provider,
                 model = spec.model,
                 dim = spec.dim,
                 normalized = spec.normalized,
                 hits = out.citations.size,
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
                stage = OP_ASK,
                upstreamStatus = null,
            )
            throw ex
        } finally {
            metrics.recordLatency(
                op = OP_ASK,
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