package dev.pedro.rag.application.chat

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.domain.chat.InferenceParams
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class ChatUseCaseTest {
    private val port: LlmChatPort = mockk()
    private val sut = ChatUseCase(port)

    @Test
    fun `should delegate to LLM port and return content`() {
        val input = buildChatInput()
        every { port.complete(input) } returns ChatOutput("ok: hello")

        val out = sut.handle(input)

        assertEquals("ok: hello", out.content)
        verify(exactly = 1) { port.complete(input) }
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
