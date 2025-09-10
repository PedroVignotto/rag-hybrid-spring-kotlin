package dev.pedro.rag.infra.llm.metrics

import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LlmMetrics(
    private val registry: MeterRegistry
) {
    private val activeStreams = AtomicInteger(0)

    init {
        registry.gauge("llm.chat.active_streams", activeStreams)
    }

    fun incrementActiveStreams(): Int = activeStreams.incrementAndGet()

    fun decrementActiveStreams(): Int = activeStreams.decrementAndGet()

    fun recordLatency(endpoint: String, provider: String, model: String, status: String, nanos: Long) =
        Timer.builder("llm.chat.latency")
            .tag("endpoint", endpoint)
            .tag("provider", provider)
            .tag("model", model)
            .tag("status", status)
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry)
            .record(nanos, TimeUnit.NANOSECONDS)

    fun recordTokens(
        provider: String,
        model: String,
        endpoint: String,
        promptTokens: Long?,
        completionTokens: Long?
    ) {
        promptTokens?.takeIf { it > 0 }?.let {
            summary(provider, model, endpoint, "prompt").record(it.toDouble())
        }
        completionTokens?.takeIf { it > 0 }?.let {
            summary(provider, model, endpoint, "completion").record(it.toDouble())
        }
    }

    fun countError(provider: String, model: String, type: String, upstreamStatus: String? = null) {
        val builder = io.micrometer.core.instrument.Counter.builder("llm.chat.errors")
            .tag("provider", provider)
            .tag("model", model)
            .tag("type", type)
        upstreamStatus?.also { builder.tag("upstream_status", it) }
        builder.register(registry).increment()
    }

    private fun summary(provider: String, model: String, endpoint: String, tokenType: String): DistributionSummary =
        DistributionSummary.builder("llm.chat.tokens")
            .baseUnit("tokens")
            .tag("provider", provider)
            .tag("model", model)
            .tag("endpoint", endpoint)
            .tag("type", tokenType)
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry)
}