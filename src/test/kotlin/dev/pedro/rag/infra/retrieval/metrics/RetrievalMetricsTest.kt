package dev.pedro.rag.infra.retrieval.metrics

import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_ERROR
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_SUCCESS
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_MODEL
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_PROVIDER
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_STATUS
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_UPSTREAM_STATUS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_CHUNKS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_CHUNK_SIZE
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_DELETED
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_ERRORS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_HITS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_K
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_LATENCY
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_STORE_SIZE
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.OP_INGEST
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_COLLECTION
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_DIM
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_ERROR_TYPE
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_NORMALIZED
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_OP
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_STAGE
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RetrievalMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val sut = RetrievalMetrics(registry)

    private val provider = "fake"
    private val model = "fake-v1"
    private val dim = 16
    private val normalized = true

    @Test
    fun `should record latency for success and error`() {
        sut.recordLatency(
            op = OP_INGEST,
            provider = provider,
            model = model,
            dim = dim,
            normalized = normalized,
            status = STATUS_SUCCESS,
            nanos = 1_000_000,
        )
        sut.recordLatency(
            op = OP_INGEST,
            provider = provider,
            model = model,
            dim = dim,
            normalized = normalized,
            status = STATUS_ERROR,
            nanos = 2_000_000,
        )

        val latencySuccessTimer =
            registry.find(METRIC_LATENCY)
                .tags(
                    TAG_OP, OP_INGEST,
                    TAG_PROVIDER, provider,
                    TAG_MODEL, model,
                    TAG_DIM, dim.toString(),
                    TAG_NORMALIZED, normalized.toString(),
                    TAG_STATUS, STATUS_SUCCESS,
                )
                .timer()
        val latencyErrorTimer =
            registry.find(METRIC_LATENCY)
                .tags(
                    TAG_OP, OP_INGEST,
                    TAG_PROVIDER, provider,
                    TAG_MODEL, model,
                    TAG_DIM, dim.toString(),
                    TAG_NORMALIZED, normalized.toString(),
                    TAG_STATUS, STATUS_ERROR,
                )
                .timer()
        assertThat(latencySuccessTimer).isNotNull
        assertThat(latencySuccessTimer!!.count()).isGreaterThan(0)
        assertThat(latencyErrorTimer).isNotNull
        assertThat(latencyErrorTimer!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should count error with upstream status`() {
        sut.countError(
            provider = provider,
            model = model,
            dim = dim,
            normalized = normalized,
            type = "IOException",
            stage = "ingest",
            upstreamStatus = "502",
        )

        val errorCounter =
            registry.find(METRIC_ERRORS)
                .tags(
                    TAG_PROVIDER, provider,
                    TAG_MODEL, model,
                    TAG_DIM, dim.toString(),
                    TAG_NORMALIZED, normalized.toString(),
                    TAG_ERROR_TYPE, "IOException",
                    TAG_STAGE, "ingest",
                    TAG_UPSTREAM_STATUS, "502",
                )
                .counter()
        assertThat(errorCounter).isNotNull
        assertThat(errorCounter!!.count()).isGreaterThan(0.0)
    }

    @Test
    fun `should count error without upstream status`() {
        sut.countError(
            provider = provider,
            model = model,
            dim = dim,
            normalized = normalized,
            type = "IllegalArgumentException",
            stage = "search",
            upstreamStatus = null,
        )

        val errorCounter =
            registry.find(METRIC_ERRORS)
                .tags(
                    TAG_PROVIDER, provider,
                    TAG_MODEL, model,
                    TAG_DIM, dim.toString(),
                    TAG_NORMALIZED, normalized.toString(),
                    TAG_ERROR_TYPE, "IllegalArgumentException",
                    TAG_STAGE, "search",
                )
                .counter()
        assertThat(errorCounter).isNotNull
        assertThat(errorCounter!!.count()).isGreaterThan(0.0)
    }

    @Test
    fun `should record chunks and chunk size`() {
        sut.recordChunks(provider, model, dim, normalized, chunks = 4)
        sut.recordChunkSize(provider, model, dim, normalized, sizeChars = 120)

        val chunksSummary =
            registry.find(METRIC_CHUNKS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, model, TAG_DIM, dim.toString(), TAG_NORMALIZED, normalized.toString())
                .summary()
        val chunkSizeSummary =
            registry.find(METRIC_CHUNK_SIZE)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, model, TAG_DIM, dim.toString(), TAG_NORMALIZED, normalized.toString())
                .summary()
        assertThat(chunksSummary).isNotNull
        assertThat(chunksSummary!!.count()).isGreaterThan(0)
        assertThat(chunkSizeSummary).isNotNull
        assertThat(chunkSizeSummary!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should record k and hits`() {
        sut.recordK(provider, model, dim, normalized, k = 5)
        sut.recordHits(provider, model, dim, normalized, hits = 2)

        val kSummary =
            registry.find(METRIC_K)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, model, TAG_DIM, dim.toString(), TAG_NORMALIZED, normalized.toString())
                .summary()
        val hitsSummary =
            registry.find(METRIC_HITS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, model, TAG_DIM, dim.toString(), TAG_NORMALIZED, normalized.toString())
                .summary()
        assertThat(kSummary).isNotNull
        assertThat(kSummary!!.count()).isGreaterThan(0)
        assertThat(hitsSummary).isNotNull
        assertThat(hitsSummary!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should set and add to store size gauge`() {
        val collection = "menu"
        sut.setStoreSize(provider, model, dim, collection, value = 10L)
        var storeSizeGauge =
            registry.find(METRIC_STORE_SIZE)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, model, TAG_DIM, dim.toString(), TAG_COLLECTION, collection)
                .gauge()
        assertThat(storeSizeGauge).isNotNull
        assertThat(storeSizeGauge!!.value()).isEqualTo(10.0)

        sut.addToStoreSize(provider, model, dim, collection, delta = 5L)
        storeSizeGauge =
            registry.find(METRIC_STORE_SIZE)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, model, TAG_DIM, dim.toString(), TAG_COLLECTION, collection)
                .gauge()
        assertThat(storeSizeGauge).isNotNull
        assertThat(storeSizeGauge!!.value()).isEqualTo(15.0)
    }

    @Test
    fun `should create gauge on add when it does not exist yet`() {
        val collection = "orders"
        sut.addToStoreSize(provider, model, dim, collection, delta = 7L)

        val storeSizeGauge =
            registry.find(METRIC_STORE_SIZE)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, model, TAG_DIM, dim.toString(), TAG_COLLECTION, collection)
                .gauge()
        assertThat(storeSizeGauge).isNotNull
        assertThat(storeSizeGauge!!.value()).isEqualTo(7.0)
    }

    @Test
    fun `should record deleted`() {
        sut.recordDeleted(provider, model, dim, normalized, deleted = 3)

        val deletedSummary =
            registry.find(METRIC_DELETED)
                .tags(
                    TAG_PROVIDER,
                    provider,
                    TAG_MODEL,
                    model,
                    TAG_DIM,
                    dim.toString(),
                    TAG_NORMALIZED,
                    normalized.toString(),
                )
                .summary()
        assertThat(deletedSummary).isNotNull
        assertThat(deletedSummary!!.count()).isGreaterThan(0)
    }
}
