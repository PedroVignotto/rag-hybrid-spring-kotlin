package dev.pedro.rag.infra.llm.metrics

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatUsage
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MetricsLlmChatPortTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = LlmMetrics(registry)
    private val delegate: LlmChatPort = mockk()

    private val provider = "ollama"
    private val modelTag = "llama3.2:3b"

    private val sut =
        MetricsLlmChatPort(
            delegate = delegate,
            metrics = metrics,
            providerTag = provider,
            modelTag = modelTag,
        )

    @Test
    fun `should record latency on complete success (no tokens)`() {
        val input = buildChatInput()
        every { delegate.complete(input) } returns ChatOutput(content = "ok")

        val result = sut.complete(input)

        assertThat(result.content).isEqualTo("ok")
        verify(exactly = 1) { delegate.complete(input) }
        val timer =
            registry.find(METRIC_CHAT_LATENCY)
                .tags(TAG_ENDPOINT, ENDPOINT_COMPLETE, TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_STATUS, STATUS_SUCCESS)
                .timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isGreaterThan(0)
        val tokensPrompt =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_ENDPOINT, ENDPOINT_COMPLETE, TAG_TOKEN_TYPE, TOKEN_TYPE_PROMPT)
                .summary()
        val tokensCompletion =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_ENDPOINT, ENDPOINT_COMPLETE, TAG_TOKEN_TYPE, TOKEN_TYPE_COMPLETION)
                .summary()
        assertThat(tokensPrompt).isNull()
        assertThat(tokensCompletion).isNull()
    }

    @Test
    fun `should count error and record latency on complete failure`() {
        class FakeUpstreamException : RuntimeException("boom")
        val input = buildChatInput()
        every { delegate.complete(input) } throws FakeUpstreamException()

        assertThatThrownBy { sut.complete(input) }.isInstanceOf(FakeUpstreamException::class.java)
        verify(exactly = 1) { delegate.complete(input) }
        val err =
            registry.find(METRIC_CHAT_ERRORS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_TYPE, "FakeUpstreamException")
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
        val timer =
            registry.find(METRIC_CHAT_LATENCY)
                .tags(TAG_ENDPOINT, ENDPOINT_COMPLETE, TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_STATUS, STATUS_ERROR)
                .timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should record latency, tokens and restore gauge on stream success`() {
        val input = buildChatInput()
        every { delegate.stream(input, any(), any()) } answers {
            val onDelta = secondArg<(String) -> Unit>()
            val onUsage = thirdArg<((ChatUsage) -> Unit)?>()
            onDelta("hel")
            onDelta("lo")
            onUsage?.invoke(ChatUsage(promptTokens = 10, completionTokens = 20, totalDurationMs = 5, loadDurationMs = 10))
        }

        sut.stream(input, {}, {})

        val latency =
            registry.find(METRIC_CHAT_LATENCY)
                .tags(TAG_ENDPOINT, ENDPOINT_STREAM, TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_STATUS, STATUS_SUCCESS)
                .timer()
        assertThat(latency).isNotNull
        assertThat(latency!!.count()).isGreaterThan(0)
        val prompt =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_ENDPOINT, ENDPOINT_STREAM, TAG_TOKEN_TYPE, TOKEN_TYPE_PROMPT)
                .summary()
        val completion =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_ENDPOINT, ENDPOINT_STREAM, TAG_TOKEN_TYPE, TOKEN_TYPE_COMPLETION)
                .summary()
        assertThat(prompt).isNotNull
        assertThat(completion).isNotNull
        assertThat(prompt!!.count()).isGreaterThan(0)
        assertThat(completion!!.count()).isGreaterThan(0)
        val gauge = registry.find(METRIC_ACTIVE_STREAMS).gauge()
        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(0.0)
    }

    @Test
    fun `should count error, record latency and restore gauge on stream failure`() {
        class FakeUpstreamException : RuntimeException("boom")
        val input = buildChatInput()
        every { delegate.stream(input, any(), any()) } throws FakeUpstreamException()

        assertThatThrownBy {
            sut.stream(input, {}, {})
        }.isInstanceOf(FakeUpstreamException::class.java)
        val err =
            registry.find(METRIC_CHAT_ERRORS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_TYPE, "FakeUpstreamException")
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
        val timer =
            registry.find(METRIC_CHAT_LATENCY)
                .tags(TAG_ENDPOINT, ENDPOINT_STREAM, TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_STATUS, STATUS_ERROR)
                .timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isGreaterThan(0)
        val gauge = registry.find(METRIC_ACTIVE_STREAMS).gauge()
        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(0.0)
    }

    @Test
    fun `should record latency and restore gauge on stream success without usage`() {
        val input = buildChatInput()
        every { delegate.stream(input, any(), null) } answers {
            val onDelta = secondArg<(String) -> Unit>()
            onDelta("only-delta")
        }

        sut.stream(input, {}, null)

        val latency =
            registry.find(METRIC_CHAT_LATENCY)
                .tags(TAG_ENDPOINT, ENDPOINT_STREAM, TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_STATUS, STATUS_SUCCESS)
                .timer()
        assertThat(latency).isNotNull
        assertThat(latency!!.count()).isGreaterThan(0)
        val prompt =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_ENDPOINT, ENDPOINT_STREAM, TAG_TOKEN_TYPE, TOKEN_TYPE_PROMPT)
                .summary()
        val completion =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_ENDPOINT, ENDPOINT_STREAM, TAG_TOKEN_TYPE, TOKEN_TYPE_COMPLETION)
                .summary()
        assertThat(prompt).isNull()
        assertThat(completion).isNull()
        val gauge = registry.find(METRIC_ACTIVE_STREAMS).gauge()
        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(0.0)
    }

    @Test
    fun `should count error, not record tokens, and restore gauge when stream fails before usage`() {
        class FakeUpstreamException : RuntimeException("boom")
        val input = buildChatInput()

        every { delegate.stream(input, any(), any()) } throws FakeUpstreamException()

        assertThatThrownBy {
            sut.stream(input, {}, {})
        }.isInstanceOf(FakeUpstreamException::class.java)

        val err =
            registry.find(METRIC_CHAT_ERRORS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_TYPE, "FakeUpstreamException")
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
        val latency =
            registry.find(METRIC_CHAT_LATENCY)
                .tags(TAG_ENDPOINT, ENDPOINT_STREAM, TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_STATUS, STATUS_ERROR)
                .timer()
        assertThat(latency).isNotNull
        assertThat(latency!!.count()).isGreaterThan(0)
        val prompt =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_ENDPOINT, ENDPOINT_STREAM, TAG_TOKEN_TYPE, TOKEN_TYPE_PROMPT)
                .summary()
        val completion =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_ENDPOINT, ENDPOINT_STREAM, TAG_TOKEN_TYPE, TOKEN_TYPE_COMPLETION)
                .summary()
        assertThat(prompt).isNull()
        assertThat(completion).isNull()
        val gauge = registry.find(METRIC_ACTIVE_STREAMS).gauge()
        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(0.0)
    }

    @Test
    fun `should ignore zero tokens on stream usage`() {
        val input = buildChatInput()
        every { delegate.stream(input, any(), any()) } answers {
            val onUsage = thirdArg<((ChatUsage) -> Unit)?>()
            onUsage?.invoke(ChatUsage(promptTokens = 0, completionTokens = 0, totalDurationMs = 1, loadDurationMs = 1))
        }

        sut.stream(input, {}, {})

        val prompt =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_ENDPOINT, ENDPOINT_STREAM, TAG_TOKEN_TYPE, TOKEN_TYPE_PROMPT)
                .summary()
        val completion =
            registry.find(METRIC_CHAT_TOKENS)
                .tags(TAG_PROVIDER, provider, TAG_MODEL, modelTag, TAG_ENDPOINT, ENDPOINT_STREAM, TAG_TOKEN_TYPE, TOKEN_TYPE_COMPLETION)
                .summary()
        assertThat(prompt).isNull()
        assertThat(completion).isNull()
    }

    private fun buildChatInput() = ChatInput(messages = emptyList())
}
