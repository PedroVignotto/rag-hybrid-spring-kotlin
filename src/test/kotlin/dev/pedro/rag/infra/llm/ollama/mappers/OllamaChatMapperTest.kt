package dev.pedro.rag.infra.llm.ollama.mappers

import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.domain.chat.InferenceParams
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatMessageRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatOptionsRequest
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequest
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

private const val MODEL = "llama3.2:3b"

class OllamaChatMapperTest {
    @Test
    fun shouldMapChatInputToOllamaChatRequest() {
        val input =
            ChatInput(
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
                        OllamaChatMessageRequest("system", "S"),
                        OllamaChatMessageRequest("user", "U"),
                        OllamaChatMessageRequest("assistant", "A"),
                    ),
                options =
                    OllamaChatOptionsRequest(
                        temperature = 0.3,
                        topP = 0.8,
                        numPredict = 128,
                    ),
            )

        val actual = input.toOllamaChatRequest(model = MODEL)

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun shouldMapChatInputWithNullParamFieldsToOllamaChatRequestWithNullOptionFields() {
        val input =
            ChatInput(
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
                messages = listOf(OllamaChatMessageRequest("user", "Hello")),
                options =
                    OllamaChatOptionsRequest(
                        temperature = null,
                        topP = null,
                        numPredict = null,
                    ),
            )

        val actual = input.toOllamaChatRequest(model = MODEL)

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    private fun buildOllamaChatRequest(
        messages: List<OllamaChatMessageRequest>,
        options: OllamaChatOptionsRequest,
    ) = OllamaChatRequest(
        model = MODEL,
        messages = messages,
        options = options,
    )
}
