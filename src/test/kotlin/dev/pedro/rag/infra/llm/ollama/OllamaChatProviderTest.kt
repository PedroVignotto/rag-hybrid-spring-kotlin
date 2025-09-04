package dev.pedro.rag.infra.llm.ollama

import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.domain.chat.InferenceParams
import dev.pedro.rag.infra.llm.ollama.client.OllamaClient
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequestMessage
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatResponse
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatResponseMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith

private const val MODEL = "llama3.2:3b"

class OllamaChatProviderTest {
    private val client = mockk<OllamaClient>()
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

    @Test
    fun `should throw when assistant content is missing or blank`() {
        val input = buildInput(text = "Hello")
        every { client.chat(any()) } returns buildResponse(content = null)

        val result = assertFailsWith<OllamaInvalidResponseException> { sut.complete(input) }

        assertThat(result).hasMessage("Ollama response is missing `message.content`")
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
            message = OllamaChatResponseMessage(role = "assistant", content = content),
            done = true,
        )

    private fun assertMapped(
        sent: OllamaChatRequest,
        expectedParams: InferenceParams,
    ) {
        assertThat(sent.model).isEqualTo(MODEL)
        assertThat(sent.messages).containsExactly(OllamaChatRequestMessage("user", "Hi"))
        assertThat(sent.options?.temperature).isEqualTo(expectedParams.temperature)
        assertThat(sent.options?.topP).isEqualTo(expectedParams.topP)
        assertThat(sent.options?.numPredict).isEqualTo(expectedParams.maxTokens)
    }
}
