package dev.pedro.rag.api.chat.mappers

import dev.pedro.rag.api.chat.request.ApiChatMessage
import dev.pedro.rag.api.chat.request.ApiChatParams
import dev.pedro.rag.api.chat.request.ApiChatRequest
import dev.pedro.rag.api.chat.response.ApiChatResponse
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.chat.ChatRole
import dev.pedro.rag.domain.chat.InferenceParams
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ChatApiMapperTest {

    @Test
    fun shouldMapApiRequestToDomain() {
        val api = ApiChatRequest(
            messages = listOf(
                ApiChatMessage("SYSTEM", "S"),
                ApiChatMessage("user", "U"),
                ApiChatMessage("Assistant", "A")
            ),
            params = ApiChatParams(temperature = 0.3, topP = 0.8, maxTokens = 128)
        )
        val expected = ChatInput(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "S"),
                ChatMessage(ChatRole.USER, "U"),
                ChatMessage(ChatRole.ASSISTANT, "A")
            ),
            params = InferenceParams(temperature = 0.3, topP = 0.8, maxTokens = 128)
        )

        val actual = api.toDomain()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun shouldMapNullParamsToDomainParamsWithNulls() {
        val api = ApiChatRequest(
            messages = listOf(ApiChatMessage("user", "Hello")),
            params = null
        )
        val expected = ChatInput(
            messages = listOf(ChatMessage(ChatRole.USER, "Hello")),
            params = InferenceParams(temperature = null, topP = null, maxTokens = null)
        )

        val actual = api.toDomain()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun shouldFailOnInvalidRole() {
        val api = ApiChatRequest(
            messages = listOf(ApiChatMessage("moderator", "hi")), // inválido
            params = null
        )

        assertFailsWith<IllegalArgumentException> { api.toDomain() }
    }

    @Test
    fun shouldMapDomainOutputToApiResponse() {
        val domain = ChatOutput(content = "ok")
        val expected = ApiChatResponse(content = "ok")

        val actual = domain.toApi()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }
}