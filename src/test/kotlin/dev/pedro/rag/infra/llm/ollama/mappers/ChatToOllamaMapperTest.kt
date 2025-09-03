package dev.pedro.rag.infra.llm.ollama.mappers

import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.domain.chat.InferenceParams
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatOptions
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequestMessage
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

private const val MODEL = "llama3.2:3b"

class ChatToOllamaMapperTest {
    @Test
    fun shouldMapChatInputToOllamaChatRequest() {
        val input =
            buildChatInput(
                messages =
                    listOf(
                        ChatMessage(ChatRole.SYSTEM, "S"),
                        ChatMessage(ChatRole.USER, "U"),
                        ChatMessage(ChatRole.ASSISTANT, "A"),
                    ),
                params =
                    InferenceParams(
                        temperature = 0.3,
                        topP = 0.8,
                        maxTokens = 128,
                    ),
            )
        val expected =
            buildOllamaChatRequest(
                messages =
                    listOf(
                        OllamaChatRequestMessage("system", "S"),
                        OllamaChatRequestMessage("user", "U"),
                        OllamaChatRequestMessage("assistant", "A"),
                    ),
                options =
                    OllamaChatOptions(
                        temperature = 0.3,
                        topP = 0.8,
                        numPredict = 128,
                    ),
            )

        val actual = ChatToOllamaMapper.toRequest(input, model = MODEL)

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun shouldMapChatInputWithNullParamsToOllamaChatRequestWithNullOptions() {
        val input =
            buildChatInput(
                messages = listOf(ChatMessage(ChatRole.USER, "Hello")),
                params =
                    InferenceParams(
                        temperature = null,
                        topP = null,
                        maxTokens = null,
                    ),
            )
        val expected =
            buildOllamaChatRequest(
                messages = listOf(OllamaChatRequestMessage("user", "Hello")),
                options =
                    OllamaChatOptions(
                        temperature = null,
                        topP = null,
                        numPredict = null,
                    ),
            )

        val actual = ChatToOllamaMapper.toRequest(input, model = MODEL)

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    private fun buildChatInput(
        messages: List<ChatMessage>,
        params: InferenceParams,
    ) = ChatInput(messages = messages, params = params)

    private fun buildOllamaChatRequest(
        messages: List<OllamaChatRequestMessage>,
        options: OllamaChatOptions,
    ) = OllamaChatRequest(
        model = MODEL,
        messages = messages,
        options = options,
    )
}
