package dev.pedro.rag.infra.retrieval.metrics

import dev.pedro.rag.application.retrieval.delete.dto.DeleteOutput
import dev.pedro.rag.application.retrieval.delete.usecase.DeleteUseCase
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_ERROR
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_SUCCESS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.OP_DELETE

class MetricsDeleteUseCase(
    private val delegate: DeleteUseCase,
    private val metrics: RetrievalMetrics,
    private val embedPort: EmbedPort,
) : DeleteUseCase {

    override fun handle(documentId: DocumentId): DeleteOutput {
        val spec = embedPort.spec()
        val start = System.nanoTime()
        var status = STATUS_SUCCESS
        try {
            val out = delegate.handle(documentId)
            metrics.recordDeleted(
                provider = spec.provider,
                model = spec.model,
                dim = spec.dim,
                normalized = spec.normalized,
                deleted = out.deleted,
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
                stage = OP_DELETE,
                upstreamStatus = null,
            )
            throw ex
        } finally {
            metrics.recordLatency(
                op = OP_DELETE,
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