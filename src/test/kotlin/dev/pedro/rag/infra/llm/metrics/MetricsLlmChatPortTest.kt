package dev.pedro.rag.infra.llm.metrics

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatUsage
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
            registry.find("llm.chat.latency")
                .tags("endpoint", "complete", "provider", provider, "model", modelTag, "status", "success")
                .timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isGreaterThan(0)
        val tokensPrompt =
            registry.find("llm.chat.tokens")
                .tags("provider", provider, "model", modelTag, "endpoint", "complete", "type", "prompt")
                .summary()
        val tokensCompletion =
            registry.find("llm.chat.tokens")
                .tags("provider", provider, "model", modelTag, "endpoint", "complete", "type", "completion")
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
            registry.find("llm.chat.errors")
                .tags("provider", provider, "model", modelTag, "type", "FakeUpstreamException")
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
        val timer =
            registry.find("llm.chat.latency")
                .tags("endpoint", "complete", "provider", provider, "model", modelTag, "status", "error")
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
            registry.find("llm.chat.latency")
                .tags("endpoint", "stream", "provider", provider, "model", modelTag, "status", "success")
                .timer()
        assertThat(latency).isNotNull
        assertThat(latency!!.count()).isGreaterThan(0)
        val prompt =
            registry.find("llm.chat.tokens")
                .tags("provider", provider, "model", modelTag, "endpoint", "stream", "type", "prompt")
                .summary()
        val completion =
            registry.find("llm.chat.tokens")
                .tags("provider", provider, "model", modelTag, "endpoint", "stream", "type", "completion")
                .summary()
        assertThat(prompt).isNotNull
        assertThat(completion).isNotNull
        assertThat(prompt!!.count()).isGreaterThan(0)
        assertThat(completion!!.count()).isGreaterThan(0)
        val gauge = registry.find("llm.chat.active_streams").gauge()
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
            registry.find("llm.chat.errors")
                .tags("provider", provider, "model", modelTag, "type", "FakeUpstreamException")
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
        val timer =
            registry.find("llm.chat.latency")
                .tags("endpoint", "stream", "provider", provider, "model", modelTag, "status", "error")
                .timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isGreaterThan(0)
        val gauge = registry.find("llm.chat.active_streams").gauge()
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
            registry.find("llm.chat.latency")
                .tags("endpoint", "stream", "provider", provider, "model", modelTag, "status", "success")
                .timer()
        assertThat(latency).isNotNull
        assertThat(latency!!.count()).isGreaterThan(0)
        val prompt =
            registry.find("llm.chat.tokens")
                .tags("provider", provider, "model", modelTag, "endpoint", "stream", "type", "prompt")
                .summary()
        val completion =
            registry.find("llm.chat.tokens")
                .tags("provider", provider, "model", modelTag, "endpoint", "stream", "type", "completion")
                .summary()
        assertThat(prompt).isNull()
        assertThat(completion).isNull()
        val gauge = registry.find("llm.chat.active_streams").gauge()
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
            registry.find("llm.chat.errors")
                .tags("provider", provider, "model", modelTag, "type", "FakeUpstreamException")
                .counter()
        assertThat(err).isNotNull
        assertThat(err!!.count()).isGreaterThan(0.0)
        val latency =
            registry.find("llm.chat.latency")
                .tags("endpoint", "stream", "provider", provider, "model", modelTag, "status", "error")
                .timer()
        assertThat(latency).isNotNull
        assertThat(latency!!.count()).isGreaterThan(0)
        val prompt =
            registry.find("llm.chat.tokens")
                .tags("provider", provider, "model", modelTag, "endpoint", "stream", "type", "prompt")
                .summary()
        val completion =
            registry.find("llm.chat.tokens")
                .tags("provider", provider, "model", modelTag, "endpoint", "stream", "type", "completion")
                .summary()
        assertThat(prompt).isNull()
        assertThat(completion).isNull()
        val gauge = registry.find("llm.chat.active_streams").gauge()
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
            registry.find("llm.chat.tokens")
                .tags("provider", provider, "model", modelTag, "endpoint", "stream", "type", "prompt")
                .summary()
        val completion =
            registry.find("llm.chat.tokens")
                .tags("provider", provider, "model", modelTag, "endpoint", "stream", "type", "completion")
                .summary()
        assertThat(prompt).isNull()
        assertThat(completion).isNull()
    }

    private fun buildChatInput() = ChatInput(messages = emptyList())
}
