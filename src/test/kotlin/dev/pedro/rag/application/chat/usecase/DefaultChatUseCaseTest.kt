package dev.pedro.rag.application.chat.usecase

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.domain.chat.ChatUsage
import dev.pedro.rag.domain.chat.InferenceParams
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class DefaultChatUseCaseTest {
    private val port: LlmChatPort = mockk()
    private val sut = DefaultChatUseCase(port)

    @Test
    fun `should delegate to LLM port and return content`() {
        val input = buildChatInput()
        every { port.complete(input) } returns ChatOutput("ok: hello")

        val out = sut.handle(input)

        assertEquals("ok: hello", out.content)
        verify(exactly = 1) { port.complete(input) }
        confirmVerified(port)
    }

    @Test
    fun `should delegate stream and forward deltas and usage`() {
        val input = buildChatInput()
        every { port.stream(input, any(), any()) } answers {
            val onDelta = secondArg<(String) -> Unit>()
            val onUsage = thirdArg<((ChatUsage) -> Unit)?>()
            onDelta("he")
            onDelta("llo")
            onUsage?.invoke(
                ChatUsage(
                    promptTokens = 10,
                    completionTokens = 20,
                    totalDurationMs = 5,
                    loadDurationMs = 1,
                ),
            )
        }
        val deltas = mutableListOf<String>()
        var usage: ChatUsage? = null

        sut.handleStream(input, { deltas.add(it) }, { usage = it })

        assertEquals(listOf("he", "llo"), deltas)
        assertEquals(10, usage?.promptTokens)
        assertEquals(20, usage?.completionTokens)
        verify(exactly = 1) { port.stream(input, any(), any()) }
        confirmVerified(port)
    }

    @Test
    fun `should propagate exception from port on stream`() {
        class FakeUpstreamException : RuntimeException("boom")
        val input = buildChatInput()
        every { port.stream(input, any(), any()) } throws FakeUpstreamException()

        assertThatThrownBy {
            sut.handleStream(input, {}, {})
        }.isInstanceOf(FakeUpstreamException::class.java)

        verify(exactly = 1) { port.stream(input, any(), any()) }
        confirmVerified(port)
    }

    private fun buildChatInput() =
        ChatInput(
            messages =
                listOf(
                    ChatMessage(ChatRole.SYSTEM, "be concise"),
                    ChatMessage(ChatRole.USER, "hello"),
                ),
            params = InferenceParams(temperature = 0.1, maxTokens = 128),
        )
}
