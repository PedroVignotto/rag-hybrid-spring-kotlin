package dev.pedro.rag.infra.retrieval.metrics

import dev.pedro.rag.application.retrieval.delete.dto.DeleteOutput
import dev.pedro.rag.application.retrieval.delete.usecase.DeleteUseCase
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_ERROR
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_SUCCESS
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_MODEL
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_PROVIDER
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_STATUS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_DELETED
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_ERRORS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_LATENCY
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.OP_DELETE
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_DIM
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_NORMALIZED
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_OP
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_STAGE
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.TAG_ERROR_TYPE
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
class MetricsDeleteUseCaseTest(
    @param:MockK private val delegate: DeleteUseCase,
    @param:MockK private val embedPort: EmbedPort,
) {
    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: RetrievalMetrics
    private lateinit var sut: MetricsDeleteUseCase

    private val spec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 3, normalized = true)

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        metrics = RetrievalMetrics(registry)
        sut = MetricsDeleteUseCase(delegate = delegate, metrics = metrics, embedPort = embedPort)
        every { embedPort.spec() } returns spec
    }

    @Test
    fun `should delegate handle and record latency success`() {
        val id = DocumentId("doc-1")
        val out = DeleteOutput(deleted = 2)
        every { delegate.handle(id) } returns out

        val result = sut.handle(id)

        assertThat(result).isEqualTo(out)
        verify(exactly = 1) { delegate.handle(id) }
        val timer =
            registry.find(METRIC_LATENCY)
                .tags(
                    TAG_OP, OP_DELETE,
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
                    TAG_STAGE, OP_DELETE,
                )
                .counter()
        assertThat(err).isNull()
    }

    @Test
    fun `should record deleted summary on success`() {
        val id = DocumentId("doc-1")
        every { delegate.handle(id) } returns DeleteOutput(deleted = 3)

        sut.handle(id)

        val deletedSummary =
            registry.find(METRIC_DELETED)
                .tags(
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                )
                .summary()
        assertThat(deletedSummary).isNotNull
        assertThat(deletedSummary!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should count error and record latency error when delegate throws`() {
        class FakeUpstream : RuntimeException("boom")
        val id = DocumentId("doc-err")
        every { delegate.handle(id) } throws FakeUpstream()

        assertThatThrownBy { sut.handle(id) }.isInstanceOf(FakeUpstream::class.java)
        verify(exactly = 1) { delegate.handle(id) }
        val err =
            registry.find(METRIC_ERRORS)
                .tags(
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                    TAG_STAGE, OP_DELETE,
                    TAG_ERROR_TYPE, "FakeUpstream",
                )
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
        val latency =
            registry.find(METRIC_LATENCY)
                .tags(
                    TAG_OP, OP_DELETE,
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
        val id = DocumentId("doc-unknown")
        val anonymousEx = object : RuntimeException("oops") {}
        every { delegate.handle(id) } throws anonymousEx

        assertThatThrownBy { sut.handle(id) }.isInstanceOf(RuntimeException::class.java)
        val err =
            registry.find(METRIC_ERRORS)
                .tags(
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                    TAG_STAGE, OP_DELETE,
                    TAG_ERROR_TYPE, "Unknown",
                )
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
    }
}