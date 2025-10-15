package dev.pedro.rag.infra.retrieval.metrics

import dev.pedro.rag.application.retrieval.ask.dto.AskInput
import dev.pedro.rag.application.retrieval.ask.dto.AskOutput
import dev.pedro.rag.application.retrieval.ask.dto.Citation
import dev.pedro.rag.application.retrieval.ask.usecase.AskUseCase
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_ERROR
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_SUCCESS
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_MODEL
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_PROVIDER
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_STATUS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_ERRORS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_HITS
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_K
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.METRIC_LATENCY
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics.Companion.OP_ASK
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
class MetricsAskUseCaseTest(
    @param:MockK private val delegate: AskUseCase,
    @param:MockK private val embedPort: EmbedPort,
) {
    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: RetrievalMetrics
    private lateinit var sut: MetricsAskUseCase

    private val spec = EmbeddingSpec(provider = "fake", model = "fake-v1", dim = 16, normalized = true)

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        metrics = RetrievalMetrics(registry)
        sut = MetricsAskUseCase(delegate = delegate, metrics = metrics, embedPort = embedPort)
        every { embedPort.spec() } returns spec
    }

    @Test
    fun `should delegate ask, record latency success, k and hits`() {
        val input = AskInput(query = "What time do you open?", topK = 5, filter = mapOf("store" to "hq"))
        val citations = listOf(
            Citation(documentId = "doc-1", title = "Menu", chunkIndex = 0),
            Citation(documentId = "doc-2", title = "Hours", chunkIndex = 1),
        )
        val output = AskOutput(answer = "We open at 9am.", citations = citations, usedK = 4, notes = null)
        every { delegate.handle(input) } returns output

        val out = sut.handle(input)

        assertThat(out).isEqualTo(output)
        verify(exactly = 1) { delegate.handle(input) }
        val latency =
            registry.find(METRIC_LATENCY)
                .tags(
                    TAG_OP, OP_ASK,
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                    TAG_STATUS, STATUS_SUCCESS,
                )
                .timer()
        assertThat(latency).isNotNull
        assertThat(latency!!.count()).isGreaterThan(0)
        val kSummary =
            registry.find(METRIC_K)
                .tags(
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                )
                .summary()
        assertThat(kSummary).isNotNull
        assertThat(kSummary!!.count()).isGreaterThan(0)
        val hitsSummary =
            registry.find(METRIC_HITS)
                .tags(
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                )
                .summary()
        assertThat(hitsSummary).isNotNull
        assertThat(hitsSummary!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should count error and record latency error when delegate throws`() {
        class FakeUpstream : RuntimeException("boom")
        val input = AskInput(query = "Menu hours", topK = 3)
        every { delegate.handle(input) } throws FakeUpstream()

        assertThatThrownBy { sut.handle(input) }.isInstanceOf(FakeUpstream::class.java)
        verify(exactly = 1) { delegate.handle(input) }
        val err =
            registry.find(METRIC_ERRORS)
                .tags(
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                    TAG_STAGE, OP_ASK,
                    TAG_ERROR_TYPE, "FakeUpstream",
                )
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
        val latency =
            registry.find(METRIC_LATENCY)
                .tags(
                    TAG_OP, OP_ASK,
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
        val input = AskInput(query = "Any promos?", topK = 2)
        val anonymousEx = object : RuntimeException("oops") {}
        every { delegate.handle(input) } throws anonymousEx

        assertThatThrownBy { sut.handle(input) }.isInstanceOf(RuntimeException::class.java)
        val err =
            registry.find(METRIC_ERRORS)
                .tags(
                    TAG_PROVIDER, spec.provider,
                    TAG_MODEL, spec.model,
                    TAG_DIM, spec.dim.toString(),
                    TAG_NORMALIZED, spec.normalized.toString(),
                    TAG_STAGE, OP_ASK,
                    TAG_ERROR_TYPE, "Unknown",
                )
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
    }
}