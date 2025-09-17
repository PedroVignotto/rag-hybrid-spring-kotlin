package dev.pedro.rag.infra.retrieval.metrics

import dev.pedro.rag.application.retrieval.ingest.dto.IngestInput
import dev.pedro.rag.application.retrieval.ingest.dto.IngestOutput
import dev.pedro.rag.application.retrieval.ingest.usecase.IngestUseCase
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_ERROR
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_SUCCESS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.OP_INGEST

class MetricsIngestUseCase(
    private val delegate: IngestUseCase,
    private val metrics: RetrievalMetrics,
    private val embedPort: EmbedPort,
) : IngestUseCase {
    override fun ingest(input: IngestInput): IngestOutput {
        val spec = embedPort.spec()
        val start = System.nanoTime()
        var status = STATUS_SUCCESS
        try {
            val out = delegate.ingest(input)
            metrics.recordChunks(
                provider = spec.provider,
                model = spec.model,
                dim = spec.dim,
                normalized = spec.normalized,
                chunks = out.chunksIngested,
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
                stage = OP_INGEST,
                upstreamStatus = null,
            )
            throw ex
        } finally {
            metrics.recordLatency(
                op = OP_INGEST,
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
