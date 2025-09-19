package dev.pedro.rag.infra.llm.ollama.chat.provider

import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.domain.chat.ChatUsage
import dev.pedro.rag.domain.chat.InferenceParams
import dev.pedro.rag.infra.llm.ollama.chat.client.OllamaChatHttpClient
import dev.pedro.rag.infra.llm.ollama.chat.request.OllamaChatMessageRequest
import dev.pedro.rag.infra.llm.ollama.chat.request.OllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.chat.response.OllamaChatMessageResponse
import dev.pedro.rag.infra.llm.ollama.chat.response.OllamaChatResponse
import dev.pedro.rag.infra.llm.ollama.chat.response.OllamaChatStreamChunkResponse
import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertFailsWith

class OllamaChatProviderTest {
    companion object {
        private const val MODEL = "llama3.2:3b"
        private const val BLANK = "   "

        @JvmStatic
        fun invalidMessageCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("assistant content is blank", buildOllamaChatResponse(content = BLANK)),
                Arguments.of("assistant content is missing (message.content = null)", buildOllamaChatResponse(content = null)),
                Arguments.of("assistant message is null", OllamaChatResponse(message = null, done = true)),
            )

        @JvmStatic
        private fun buildOllamaChatResponse(content: String?): OllamaChatResponse =
            OllamaChatResponse(
                message = OllamaChatMessageResponse(role = "assistant", content = content),
                done = true,
            )
    }

    private val client = mockk<OllamaChatHttpClient>()
    private val sut = OllamaChatProvider(client, defaultModel = MODEL)

    @Test
    fun `should map input, delegate to client and map response`() {
        val params = InferenceParams(temperature = 0.2, topP = 0.9, maxTokens = 64)
        val input = buildInput(text = "Hi", params = params)
        val requestSlot = slot<OllamaChatRequest>()
        every { client.chat(capture(requestSlot)) } returns buildResponse(content = "ok")

        val result = sut.complete(input)

        assertThat(result.content).isEqualTo("ok")
        assertMapped(sent = requestSlot.captured, expectedParams = params)
        verify(exactly = 1) { client.chat(any()) }
    }

    @ParameterizedTest(name = "should throw when {0}")
    @MethodSource("invalidMessageCases")
    fun `should throw when assistant message invalid`(
        case: String,
        response: OllamaChatResponse,
    ) {
        val input = buildInput(text = "Hello")
        every { client.chat(any()) } returns response

        val ex = assertFailsWith<OllamaInvalidResponseException> { sut.complete(input) }

        assertThat(ex).hasMessage("Ollama response is missing `message.content`")
    }

    @Test
    fun `should stream pass-through deltas and map usage nanos to millis`() {
        val params = InferenceParams(temperature = 0.3, topP = 0.8, maxTokens = 32)
        val input = buildInput(text = "Hi", params = params)
        val requestSlot = slot<OllamaChatRequest>()
        val deltas = mutableListOf<String>()
        var usage: ChatUsage? = null
        every { client.chatStream(capture(requestSlot), any(), any()) } answers {
            val onDelta = secondArg<(String) -> Unit>()
            val onDone = thirdArg<((OllamaChatStreamChunkResponse) -> Unit)>()
            onDelta("Hello")
            onDelta(" world")
            onDone(
                OllamaChatStreamChunkResponse(
                    done = true,
                    promptEvalCount = 12,
                    evalCount = 34,
                    totalDuration = 1_500_000_000,
                    loadDuration = 500_000_000,
                ),
            )
        }

        sut.stream(
            input = input,
            onDelta = { deltas += it },
            onUsage = { usage = it },
        )

        assertMapped(sent = requestSlot.captured, expectedParams = params)
        assertThat(deltas).containsExactly("Hello", " world")
        val u = requireNotNull(usage)
        assertThat(u.promptTokens).isEqualTo(12)
        assertThat(u.completionTokens).isEqualTo(34)
        assertThat(u.totalDurationMs).isEqualTo(1500)
        assertThat(u.loadDurationMs).isEqualTo(500)
        verify(exactly = 1) { client.chatStream(any(), any(), any()) }
    }

    @Test
    fun `should stream map usage with null fields`() {
        val input = buildInput(text = "Hi")
        val deltas = mutableListOf<String>()
        var usage: ChatUsage? = null
        every { client.chatStream(any(), any(), any()) } answers {
            val onDone = thirdArg<((OllamaChatStreamChunkResponse) -> Unit)>()
            onDone(
                OllamaChatStreamChunkResponse(
                    done = true,
                    promptEvalCount = null,
                    evalCount = null,
                    totalDuration = null,
                    loadDuration = null,
                ),
            )
        }

        sut.stream(
            input = input,
            onDelta = { deltas += it },
            onUsage = { usage = it },
        )

        val u = requireNotNull(usage)
        assertThat(u.promptTokens).isNull()
        assertThat(u.completionTokens).isNull()
        assertThat(u.totalDurationMs).isNull()
        assertThat(u.loadDurationMs).isNull()
    }

    @Test
    fun `should stream handle onUsage null and still emit deltas`() {
        val params = InferenceParams(temperature = 0.2, topP = 0.9, maxTokens = 64)
        val input = buildInput(text = "Hi", params = params)
        val deltas = mutableListOf<String>()
        every { client.chatStream(any(), any(), any()) } answers {
            val onDelta = secondArg<(String) -> Unit>()
            val onDone = thirdArg<((OllamaChatStreamChunkResponse) -> Unit)>()
            onDelta("A")
            onDelta("B")
            onDone(OllamaChatStreamChunkResponse(done = true))
        }

        sut.stream(
            input = input,
            onDelta = { deltas += it },
            onUsage = null,
        )

        assertThat(deltas).containsExactly("A", "B")
        verify(exactly = 1) { client.chatStream(any(), any(), any()) }
    }

    @Test
    fun `should propagate client exception on complete`() {
        val input = buildInput(text = "Hi")
        every { client.chat(any()) } throws OllamaHttpException(502, "bad gateway")

        val result = assertFailsWith<OllamaHttpException> { sut.complete(input) }

        assertThat(result.status).isEqualTo(502)
        assertThat(result.responseBody).contains("bad gateway")
    }

    @Test
    fun `should propagate client exception on stream`() {
        val input = buildInput(text = "Hi")
        every { client.chatStream(any(), any(), any()) } throws OllamaHttpException(500, "oops")

        val result =
            assertFailsWith<OllamaHttpException> {
                sut.stream(input, onDelta = { }, onUsage = null)
            }

        assertThat(result.status).isEqualTo(500)
        assertThat(result.responseBody).contains("oops")
    }

    private fun buildInput(
        text: String,
        params: InferenceParams = InferenceParams(0.2, 0.9, 64),
    ) = ChatInput(
        messages = listOf(ChatMessage(ChatRole.USER, text)),
        params = params,
    )

    private fun buildResponse(content: String?) =
        OllamaChatResponse(
            message = OllamaChatMessageResponse(role = "assistant", content = content),
            done = true,
        )

    private fun assertMapped(
        sent: OllamaChatRequest,
        expectedParams: InferenceParams,
    ) {
        assertThat(sent.model).isEqualTo(MODEL)
        assertThat(sent.messages).containsExactly(OllamaChatMessageRequest("user", "Hi"))
        assertThat(sent.options?.temperature).isEqualTo(expectedParams.temperature)
        assertThat(sent.options?.topP).isEqualTo(expectedParams.topP)
        assertThat(sent.options?.numPredict).isEqualTo(expectedParams.maxTokens)
    }
}
