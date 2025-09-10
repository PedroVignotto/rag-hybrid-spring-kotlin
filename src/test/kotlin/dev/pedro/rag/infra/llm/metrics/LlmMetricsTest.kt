package dev.pedro.rag.infra.llm.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream

class LlmMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val sut = LlmMetrics(registry)

    private companion object {
        const val PROVIDER = "ollama"
        const val MODEL = "llama3.2:3b"

        @JvmStatic
        fun noOpTokenValues(): Stream<Arguments> = Stream.of(Arguments.of(null), Arguments.of(0L))
    }

    @Test
    fun `should increment and decrement active_streams gauge`() {
        val gauge = registry.find("llm.chat.active_streams").gauge()
        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isEqualTo(0.0)

        sut.incrementActiveStreams()
        assertThat(gauge.value()).isEqualTo(1.0)

        sut.decrementActiveStreams()
        assertThat(gauge.value()).isEqualTo(0.0)
    }

    @ParameterizedTest
    @ValueSource(strings = ["success", "error"])
    fun `should record latency for statuses only`(status: String) {
        val endpoint = "complete"
        sut.recordLatency(
            endpoint = "complete",
            provider = PROVIDER,
            model = MODEL,
            status = status,
            nanos = 1_000_000,
        )

        val timer =
            registry.find("llm.chat.latency")
                .tags("endpoint", "complete", "provider", PROVIDER, "model", MODEL, "status", status)
                .timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isGreaterThan(0)
    }

    @Test
    fun `should record token summaries for prompt and completion`() {
        sut.recordTokens(
            provider = PROVIDER,
            model = MODEL,
            endpoint = "stream",
            promptTokens = 10,
            completionTokens = 20,
        )

        val prompt =
            registry.find("llm.chat.tokens")
                .tags("provider", PROVIDER, "model", MODEL, "endpoint", "stream", "type", "prompt")
                .summary()
        val completion =
            registry.find("llm.chat.tokens")
                .tags("provider", PROVIDER, "model", MODEL, "endpoint", "stream", "type", "completion")
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
            registry.find("llm.chat.errors")
                .tags("provider", PROVIDER, "model", MODEL, "type", "OllamaHttpException", "upstream_status", "502")
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
            registry.find("llm.chat.errors")
                .tags("provider", PROVIDER, "model", MODEL, "type", "IOException")
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
            endpoint = "complete",
            promptTokens = token,
            completionTokens = token,
        )

        val prompt =
            registry.find("llm.chat.tokens")
                .tags("provider", PROVIDER, "model", MODEL, "endpoint", "complete", "type", "prompt")
                .summary()
        val completion =
            registry.find("llm.chat.tokens")
                .tags("provider", PROVIDER, "model", MODEL, "endpoint", "complete", "type", "completion")
                .summary()
        assertThat(prompt).isNull()
        assertThat(completion).isNull()
    }
}
