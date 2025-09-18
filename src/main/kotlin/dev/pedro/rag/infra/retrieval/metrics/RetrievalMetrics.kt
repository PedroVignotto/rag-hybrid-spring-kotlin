package dev.pedro.rag.infra.retrieval.metrics

import dev.pedro.rag.infra.observability.metrics.MetricsCommon.DEFAULT_PERCENTILES
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_MODEL
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_PROVIDER
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_STATUS
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_UPSTREAM_STATUS
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class RetrievalMetrics(
    private val registry: MeterRegistry,
) {
    companion object {
        const val METRIC_LATENCY = "retrieval.latency"
        const val METRIC_ERRORS = "retrieval.errors"
        const val METRIC_CHUNKS = "retrieval.chunks"
        const val METRIC_CHUNK_SIZE = "retrieval.chunk_size"
        const val METRIC_K = "retrieval.k"
        const val METRIC_HITS = "retrieval.hits"
        const val METRIC_DELETED = "retrieval.deleted"
        const val METRIC_STORE_SIZE = "retrieval.store.size"

        const val TAG_OP = "op"
        const val TAG_DIM = "dim"
        const val TAG_NORMALIZED = "normalized"
        const val TAG_COLLECTION = "collection"
        const val TAG_STAGE = "stage"
        const val TAG_ERROR_TYPE = "type"

        const val OP_INGEST = "ingest"
        const val OP_SEARCH = "search"
        const val OP_DELETE = "delete"
    }

    private val storeGauges = ConcurrentHashMap<List<String>, AtomicLong>()

    fun recordLatency(
        op: String,
        provider: String,
        model: String,
        dim: Int,
        normalized: Boolean,
        status: String,
        nanos: Long,
    ) = Timer.builder(METRIC_LATENCY)
        .tag(TAG_OP, op)
        .tag(TAG_PROVIDER, provider)
        .tag(TAG_MODEL, model)
        .tag(TAG_DIM, dim.toString())
        .tag(TAG_NORMALIZED, normalized.toString())
        .tag(TAG_STATUS, status)
        .publishPercentiles(*DEFAULT_PERCENTILES)
        .register(registry)
        .record(nanos, TimeUnit.NANOSECONDS)

    fun countError(
        provider: String,
        model: String,
        dim: Int,
        normalized: Boolean,
        type: String,
        stage: String,
        upstreamStatus: String? = null,
    ) {
        val builder =
            Counter.builder(METRIC_ERRORS)
                .tag(TAG_PROVIDER, provider)
                .tag(TAG_MODEL, model)
                .tag(TAG_DIM, dim.toString())
                .tag(TAG_NORMALIZED, normalized.toString())
                .tag(TAG_ERROR_TYPE, type)
                .tag(TAG_STAGE, stage)
        upstreamStatus?.also { builder.tag(TAG_UPSTREAM_STATUS, it) }
        builder.register(registry).increment()
    }

    fun recordChunks(
        provider: String,
        model: String,
        dim: Int,
        normalized: Boolean,
        chunks: Int,
    ) = summary(METRIC_CHUNKS, provider, model, dim, normalized).record(chunks.toDouble())

    fun recordChunkSize(
        provider: String,
        model: String,
        dim: Int,
        normalized: Boolean,
        sizeChars: Int,
    ) = summary(METRIC_CHUNK_SIZE, provider, model, dim, normalized).record(sizeChars.toDouble())

    fun recordK(
        provider: String,
        model: String,
        dim: Int,
        normalized: Boolean,
        k: Int,
    ) = summary(METRIC_K, provider, model, dim, normalized).record(k.toDouble())

    fun recordHits(
        provider: String,
        model: String,
        dim: Int,
        normalized: Boolean,
        hits: Int,
    ) = summary(METRIC_HITS, provider, model, dim, normalized).record(hits.toDouble())

    fun setStoreSize(
        provider: String,
        model: String,
        dim: Int,
        collection: String,
        value: Long,
    ) {
        val key = listOf(provider, model, dim.toString(), collection)
        val ref =
            storeGauges.computeIfAbsent(key) {
                val a = AtomicLong(0)
                Gauge.builder(METRIC_STORE_SIZE, a) { it.toDouble() }
                    .tag(TAG_PROVIDER, provider)
                    .tag(TAG_MODEL, model)
                    .tag(TAG_DIM, dim.toString())
                    .tag(TAG_COLLECTION, collection)
                    .register(registry)
                a
            }
        ref.set(value)
    }

    fun addToStoreSize(
        provider: String,
        model: String,
        dim: Int,
        collection: String,
        delta: Long,
    ) {
        val key = listOf(provider, model, dim.toString(), collection)
        val ref =
            storeGauges.computeIfAbsent(key) {
                val a = AtomicLong(0)
                Gauge.builder(METRIC_STORE_SIZE, a) { it.toDouble() }
                    .tag(TAG_PROVIDER, provider)
                    .tag(TAG_MODEL, model)
                    .tag(TAG_DIM, dim.toString())
                    .tag(TAG_COLLECTION, collection)
                    .register(registry)
                a
            }
        ref.addAndGet(delta)
    }

    fun recordDeleted(
        provider: String,
        model: String,
        dim: Int,
        normalized: Boolean,
        deleted: Int,
    ) = summary(METRIC_DELETED, provider, model, dim, normalized).record(deleted.toDouble())

    private fun summary(
        name: String,
        provider: String,
        model: String,
        dim: Int,
        normalized: Boolean,
    ): DistributionSummary =
        DistributionSummary.builder(name)
            .tag(TAG_PROVIDER, provider)
            .tag(TAG_MODEL, model)
            .tag(TAG_DIM, dim.toString())
            .tag(TAG_NORMALIZED, normalized.toString())
            .publishPercentiles(*DEFAULT_PERCENTILES)
            .register(registry)
}
