package dev.pedro.rag.infra.llm.metrics

import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.ENDPOINT_COMPLETE
import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.ENDPOINT_STREAM
import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.METRIC_ACTIVE_STREAMS
import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.METRIC_CHAT_ERRORS
import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.METRIC_CHAT_LATENCY
import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.METRIC_CHAT_TOKENS
import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.TAG_ENDPOINT
import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.TAG_TOKEN_TYPE
import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.TAG_TYPE
import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.TOKEN_TYPE_COMPLETION
import dev.pedro.rag.infra.llm.metrics.LlmMetrics.Companion.TOKEN_TYPE_PROMPT
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_ERROR
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.STATUS_SUCCESS
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_MODEL
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_PROVIDER
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_STATUS
import dev.pedro.rag.infra.observability.metrics.MetricsCommon.TAG_UPSTREAM_STATUS
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class LlmMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val sut = LlmMetrics(registry)

    private companion object {
        const val PROVIDER = "ollama"
        const val MODEL = "llama3.2:3b"

        @JvmStatic
        fun noOpTokenValues(): Stream<Arguments> = Stream.of(Arguments.of(null), Arguments.of(0L))

        @JvmStatic
        fun statuses(): Stream<Arguments> =
            Stream.of(
                Arguments.of(STATUS_SUCCESS),
                Arguments.of(STATUS_ERROR),
            )
    }

    @Test
    fun `should increment and decrement active_streams gauge`() {
        val gauge = registry.find(METRIC_ACTIVE_STREAMS).gauge()
        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(0.0)

        sut.incrementActiveStreams()
        assertThat(gauge.value()).isEqualTo(1.0)
        sut.decrementActiveStreams()
        assertThat(gauge.value()).isEqualTo(0.0)
    }

    @ParameterizedTest
    @MethodSource("statuses")
    fun `should record latency for statuses only`(status: String) {
        sut.recordLatency(
            endpoint = ENDPOINT_COMPLETE,
            provider = PROVIDER,
            model = MODEL,
            status = status,
            nanos = 1_000_000,
        )

        val timer =
            registry.find(METRIC_CHAT_LATENCY)
                .tags(
                    TAG_ENDPOINT,
                    ENDPOINT_COMPLETE,
                    TAG_PROVIDER,
                    PROVIDER,
                    TAG_MODEL,
                    MODEL,
                    TAG_STATUS,
                    status,
                )
                .timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should record token summaries for prompt and completion`() {
        sut.recordTokens(
            provider = PROVIDER,
            model = MODEL,
            endpoint = ENDPOINT_STREAM,
            promptTokens = 10,
            completionTokens = 20,
        )

        val prompt =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(
                    TAG_PROVIDER,
                    PROVIDER,
                    TAG_MODEL,
                    MODEL,
                    TAG_ENDPOINT,
                    ENDPOINT_STREAM,
                    TAG_TOKEN_TYPE,
                    TOKEN_TYPE_PROMPT,
                )
                .summary()
        val completion =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(
                    TAG_PROVIDER,
                    PROVIDER,
                    TAG_MODEL,
                    MODEL,
                    TAG_ENDPOINT,
                    ENDPOINT_STREAM,
                    TAG_TOKEN_TYPE,
                    TOKEN_TYPE_COMPLETION,
                )
                .summary()
        assertThat(prompt).isNotNull
        assertThat(completion).isNotNull
        assertThat(prompt!!.count()).isGreaterThan(0)
        assertThat(completion!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should count errors with upstream status tag`() {
        sut.countError(
            provider = PROVIDER,
            model = MODEL,
            type = "OllamaHttpException",
            upstreamStatus = "502",
        )

        val counter =
            registry.find(METRIC_CHAT_ERRORS)
                .tags(
                    TAG_PROVIDER,
                    PROVIDER,
                    TAG_MODEL,
                    MODEL,
                    TAG_TYPE,
                    "OllamaHttpException",
                    TAG_UPSTREAM_STATUS,
                    "502",
                )
                .counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isGreaterThan(0.0)
    }

    @Test
    fun `should count errors without upstream status tag`() {
        sut.countError(
            provider = PROVIDER,
            model = MODEL,
            type = "IOException",
            upstreamStatus = null,
        )

        val counter =
            registry.find(METRIC_CHAT_ERRORS)
                .tags(
                    TAG_PROVIDER,
                    PROVIDER,
                    TAG_MODEL,
                    MODEL,
                    TAG_TYPE,
                    "IOException",
                )
                .counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isGreaterThan(0.0)
    }

    @ParameterizedTest
    @MethodSource("noOpTokenValues")
    fun `should not record token summaries when values are null or zero`(token: Long?) {
        sut.recordTokens(
            provider = PROVIDER,
            model = MODEL,
            endpoint = ENDPOINT_COMPLETE,
            promptTokens = token,
            completionTokens = token,
        )

        val prompt =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(
                    TAG_PROVIDER,
                    PROVIDER,
                    TAG_MODEL,
                    MODEL,
                    TAG_ENDPOINT,
                    ENDPOINT_COMPLETE,
                    TAG_TOKEN_TYPE,
                    TOKEN_TYPE_PROMPT,
                )
                .summary()
        val completion =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(
                    TAG_PROVIDER,
                    PROVIDER,
                    TAG_MODEL,
                    MODEL,
                    TAG_ENDPOINT,
                    ENDPOINT_COMPLETE,
                    TAG_TOKEN_TYPE,
                    TOKEN_TYPE_COMPLETION,
                )
                .summary()
        assertThat(prompt).isNull()
        assertThat(completion).isNull()
    }

    @Test
    fun `should accumulate token summaries counts and totals`() {
        sut.recordTokens(PROVIDER, MODEL, ENDPOINT_STREAM, 7, 3)
        sut.recordTokens(PROVIDER, MODEL, ENDPOINT_STREAM, 5, 0)

        val prompt =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(
                    TAG_PROVIDER,
                    PROVIDER,
                    TAG_MODEL,
                    MODEL,
                    TAG_ENDPOINT,
                    ENDPOINT_STREAM,
                    TAG_TOKEN_TYPE,
                    TOKEN_TYPE_PROMPT,
                )
                .summary()
        assertThat(prompt).isNotNull
        assertThat(prompt!!.count()).isEqualTo(2)
        assertThat(prompt.totalAmount()).isEqualTo(12.0)
    }
}
