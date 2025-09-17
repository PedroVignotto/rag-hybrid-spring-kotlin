package dev.pedro.rag.infra.retrieval.metrics

import dev.pedro.rag.application.retrieval.ingest.dto.IngestInput
import dev.pedro.rag.application.retrieval.ingest.dto.IngestOutput
import dev.pedro.rag.application.retrieval.ingest.usecase.IngestUseCase
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_ERROR
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_SUCCESS
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_MODEL
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_PROVIDER
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_STATUS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_CHUNKS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_ERRORS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_LATENCY
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.OP_INGEST
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_DIM
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_ERROR_TYPE
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_NORMALIZED
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_OP
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_STAGE
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class MetricsIngestUseCaseTest(
    @param:MockK private val delegate: IngestUseCase,
    @param:MockK private val embedPort: EmbedPort,
) {
    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: RetrievalMetrics

    private lateinit var sut: MetricsIngestUseCase

    private val spec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 3, normalized = true)

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        metrics = RetrievalMetrics(registry)
        sut = MetricsIngestUseCase(delegate = delegate, metrics = metrics, embedPort = embedPort)
        every { embedPort.spec() } returns spec
    }

    @Test
    fun `should delegate ingest and record latency success`() {
        val input = ingestInput()
        val output = IngestOutput(DocumentId("doc-1"), chunksIngested = 4)
        every { delegate.ingest(input) } returns output

        val result = sut.ingest(input)

        assertThat(result).isEqualTo(output)
        verify(exactly = 1) { delegate.ingest(input) }
        val timer =
            registry.find(METRIC_LATENCY)
                .tags(
                    TAG_OP, OP_INGEST,
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                    TAG_STATUS, STATUS_SUCCESS,
                )
                .timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isGreaterThan(0)
        val err =
            registry.find(METRIC_ERRORS)
                .tags(
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                    TAG_STAGE, "ingest",
                )
                .counter()
        assertThat(err).isNull()
    }

    @Test
    fun `should record chunks count on success`() {
        val input = ingestInput()
        every { delegate.ingest(input) } returns IngestOutput(DocumentId("doc-1"), chunksIngested = 4)

        sut.ingest(input)

        val summary =
            registry.find(METRIC_CHUNKS)
                .tags(
                    TAG_PROVIDER,
                    spec.provider,
                    TAG_MODEL,
                    spec.model,
                    TAG_DIM,
                    spec.dim.toString(),
                    TAG_NORMALIZED,
                    spec.normalized.toString(),
                )
                .summary()
        assertThat(summary).isNotNull
        assertThat(summary!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should count error and record latency error when delegate throws`() {
        class FakeUpstream : RuntimeException("boom")
        val input = ingestInput()
        every { delegate.ingest(input) } throws FakeUpstream()

        assertThatThrownBy { sut.ingest(input) }.isInstanceOf(FakeUpstream::class.java)
        verify(exactly = 1) { delegate.ingest(input) }
        val err =
            registry.find(METRIC_ERRORS)
                .tags(
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                    TAG_STAGE, "ingest",
                    TAG_ERROR_TYPE, "FakeUpstream",
                )
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
        val latency =
            registry.find(METRIC_LATENCY)
                .tags(
                    TAG_OP, OP_INGEST,
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                    TAG_STATUS, STATUS_ERROR,
                )
                .timer()
        assertThat(latency).isNotNull
        assertThat(latency!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should fallback to Unknown when exception simpleName is null`() {
        val input = ingestInput()
        val anonymousEx = object : RuntimeException("oops") {} // simpleName == null
        every { delegate.ingest(input) } throws anonymousEx

        assertThatThrownBy { sut.ingest(input) }.isInstanceOf(RuntimeException::class.java)
        val err =
            registry.find(METRIC_ERRORS)
                .tags(
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                    TAG_STAGE, OP_INGEST,
                    TAG_ERROR_TYPE, "Unknown",
                )
                .counter()

        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
    }

    private fun ingestInput() =
        IngestInput(
            documentId = DocumentId("doc-1"),
            text = "ABCDEFGHIJ",
            baseMetadata = emptyMap(),
            chunkSize = 5,
            overlap = 2,
        )
}
