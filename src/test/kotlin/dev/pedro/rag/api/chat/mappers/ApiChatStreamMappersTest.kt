package dev.pedro.rag.api.chat.mappers

import dev.pedro.rag.api.chat.response.stream.ApiChatUsageResponse
import dev.pedro.rag.domain.chat.ChatUsage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiChatStreamMappersTest {
    @Test
    fun `should map ChatUsage domain to ApiChatUsageResponse`() {
        val domain =
            ChatUsage(
                promptTokens = 12,
                completionTokens = 34,
                totalDurationMs = 1234,
                loadDurationMs = 567,
            )
        val expected =
            ApiChatUsageResponse(
                promptTokens = 12,
                completionTokens = 34,
                totalDurationMs = 1234,
                loadDurationMs = 567,
            )

        val actual = domain.toApiStreamResponse()

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }
}
