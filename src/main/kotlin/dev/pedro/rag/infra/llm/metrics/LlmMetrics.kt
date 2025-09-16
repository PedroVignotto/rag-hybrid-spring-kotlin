package dev.pedro.rag.infra.llm.metrics

import dev.pedro.rag.infra.observability.MetricsCommon.DEFAULT_PERCENTILES
import dev.pedro.rag.infra.observability.MetricsCommon.TAG_MODEL
import dev.pedro.rag.infra.observability.MetricsCommon.TAG_PROVIDER
import dev.pedro.rag.infra.observability.MetricsCommon.TAG_STATUS
import dev.pedro.rag.infra.observability.MetricsCommon.TAG_UPSTREAM_STATUS
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LlmMetrics(
    private val registry: MeterRegistry,
) {
    companion object {
        const val METRIC_CHAT_LATENCY = "llm.chat.latency"
        const val METRIC_CHAT_TOKENS = "llm.chat.tokens"
        const val METRIC_CHAT_ERRORS = "llm.chat.errors"
        const val METRIC_ACTIVE_STREAMS = "llm.chat.active_streams"

        const val TAG_ENDPOINT = "endpoint"
        const val TAG_TYPE = "type"
        const val TAG_TOKEN_TYPE = "type"

        const val ENDPOINT_COMPLETE = "complete"
        const val ENDPOINT_STREAM = "stream"
        const val TOKEN_TYPE_PROMPT = "prompt"
        const val TOKEN_TYPE_COMPLETION = "completion"
    }

    private val activeStreams = AtomicInteger(0)

    init {
        registry.gauge(METRIC_ACTIVE_STREAMS, activeStreams)
    }

    fun incrementActiveStreams(): Int = activeStreams.incrementAndGet()

    fun decrementActiveStreams(): Int = activeStreams.decrementAndGet()

    fun recordLatency(
        endpoint: String,
        provider: String,
        model: String,
        status: String,
        nanos: Long,
    ) = Timer.builder(METRIC_CHAT_LATENCY)
        .tag(TAG_ENDPOINT, endpoint)
        .tag(TAG_PROVIDER, provider)
        .tag(TAG_MODEL, model)
        .tag(TAG_STATUS, status)
        .publishPercentiles(0.5, 0.9, 0.99)
        .register(registry)
        .record(nanos, TimeUnit.NANOSECONDS)

    fun recordTokens(
        provider: String,
        model: String,
        endpoint: String,
        promptTokens: Long?,
        completionTokens: Long?,
    ) {
        promptTokens?.takeIf { it > 0 }?.let {
            summaryTokens(provider, model, endpoint, TOKEN_TYPE_PROMPT).record(it.toDouble())
        }
        completionTokens?.takeIf { it > 0 }?.let {
            summaryTokens(provider, model, endpoint, TOKEN_TYPE_COMPLETION).record(it.toDouble())
        }
    }

    fun countError(
        provider: String,
        model: String,
        type: String,
        upstreamStatus: String? = null,
    ) {
        val builder =
            Counter.builder(METRIC_CHAT_ERRORS)
                .tag(TAG_PROVIDER, provider)
                .tag(TAG_MODEL, model)
                .tag(TAG_TYPE, type)
        upstreamStatus?.also { builder.tag(TAG_UPSTREAM_STATUS, it) }
        builder.register(registry).increment()
    }

    private fun summaryTokens(
        provider: String,
        model: String,
        endpoint: String,
        tokenType: String,
    ): DistributionSummary =
        DistributionSummary.builder(METRIC_CHAT_TOKENS)
            .baseUnit("tokens")
            .tag(TAG_PROVIDER, provider)
            .tag(TAG_MODEL, model)
            .tag(TAG_ENDPOINT, endpoint)
            .tag(TAG_TOKEN_TYPE, tokenType)
            .publishPercentiles(*DEFAULT_PERCENTILES)
            .register(registry)
}
