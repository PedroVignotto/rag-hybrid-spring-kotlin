package dev.pedro.rag.infra.llm.metrics

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatUsage

class MetricsLlmChatPort(
    private val delegate: LlmChatPort,
    private val metrics: LlmMetrics,
    private val providerTag: String,
    private val modelTag: String,
) : LlmChatPort {
    override fun complete(input: ChatInput): ChatOutput {
        val start = System.nanoTime()
        var status = "success"
        try {
            return delegate.complete(input)
        } catch (ex: Exception) {
            status = "error"
            metrics.countError(
                provider = providerTag,
                model = modelTag,
                type = ex::class.simpleName ?: "Unknown",
                upstreamStatus = null,
            )
            throw ex
        } finally {
            metrics.recordLatency(
                endpoint = "complete",
                provider = providerTag,
                model = modelTag,
                status = status,
                nanos = System.nanoTime() - start,
            )
        }
    }

    override fun stream(
        input: ChatInput,
        onDelta: (String) -> Unit,
        onUsage: ((ChatUsage) -> Unit)?,
    ) {
        val start = System.nanoTime()
        var status = "success"
        metrics.incrementActiveStreams()
        try {
            val wrappedUsage: ((ChatUsage) -> Unit)? =
                onUsage?.let { downstream ->
                    { usage ->
                        metrics.recordTokens(
                            provider = providerTag,
                            model = modelTag,
                            endpoint = "stream",
                            promptTokens = usage.promptTokens?.toLong(),
                            completionTokens = usage.completionTokens?.toLong(),
                        )
                        downstream(usage)
                    }
                }

            delegate.stream(input, onDelta, wrappedUsage)
        } catch (ex: Exception) {
            status = "error"
            metrics.countError(
                provider = providerTag,
                model = modelTag,
                type = ex::class.simpleName ?: "Unknown",
                upstreamStatus = null,
            )
            throw ex
        } finally {
            metrics.decrementActiveStreams()
            metrics.recordLatency(
                endpoint = "stream",
                provider = providerTag,
                model = modelTag,
                status = status,
                nanos = System.nanoTime() - start,
            )
        }
    }
}
