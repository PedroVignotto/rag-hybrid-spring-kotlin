package dev.pedro.rag.api.chat.mappers

import dev.pedro.rag.api.chat.request.ChatMessageRequest
import dev.pedro.rag.api.chat.request.ChatParamsRequest
import dev.pedro.rag.api.chat.request.ChatRequest
import dev.pedro.rag.api.chat.response.ChatResponse
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.domain.chat.InferenceParams
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ChatMapperTest {
    @Test
    fun shouldMapRequestToDomainWithExplicitParams() {
        val request =
            ChatRequest(
                messages =
                    listOf(
                        ChatMessageRequest("SYSTEM", "S"),
                        ChatMessageRequest("user", "U"),
                        ChatMessageRequest("Assistant", "A"),
                    ),
                params = ChatParamsRequest(temperature = 0.3, topP = 0.8, maxTokens = 128),
            )
        val expected =
            ChatInput(
                messages =
                    listOf(
                        ChatMessage(ChatRole.SYSTEM, "S"),
                        ChatMessage(ChatRole.USER, "U"),
                        ChatMessage(ChatRole.ASSISTANT, "A"),
                    ),
                params = InferenceParams(temperature = 0.3, topP = 0.8, maxTokens = 128),
            )

        val actual = request.toDomain()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun shouldMapNullParamsToDomainDefaults() {
        val request =
            ChatRequest(
                messages = listOf(ChatMessageRequest("user", "Hello")),
                params = null,
            )
        val expected =
            ChatInput(
                messages = listOf(ChatMessage(ChatRole.USER, "Hello")),
                params = InferenceParams(),
            )

        val actual = request.toDomain()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun shouldFallbackMissingParamFieldsToDefaults() {
        val request =
            ChatRequest(
                messages = listOf(ChatMessageRequest("user", "Hello")),
                params =
                    ChatParamsRequest(
                        temperature = 0.5,
                        topP = null,
                        maxTokens = null,
                    ),
            )
        val expected =
            ChatInput(
                messages = listOf(ChatMessage(ChatRole.USER, "Hello")),
                params =
                    InferenceParams(
                        temperature = 0.5,
                        topP = 0.9,
                        maxTokens = 512,
                    ),
            )

        val actual = request.toDomain()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun shouldMapDomainOutputToResponse() {
        val domain = ChatOutput(content = "ok")
        val expected = ChatResponse(content = "ok")

        val actual = domain.toResponse()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun shouldFailOnInvalidRole() {
        val request =
            ChatRequest(
                messages = listOf(ChatMessageRequest("moderator", "hi")),
                params = null,
            )

        assertFailsWith<IllegalArgumentException> { request.toDomain() }
    }
}
