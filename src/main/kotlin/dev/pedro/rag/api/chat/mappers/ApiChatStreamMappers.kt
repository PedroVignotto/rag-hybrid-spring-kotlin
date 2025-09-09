package dev.pedro.rag.api.chat.mappers

import dev.pedro.rag.api.chat.response.stream.ApiChatUsageResponse
import dev.pedro.rag.domain.chat.ChatUsage

fun ChatUsage.toApiStreamResponse() = ApiChatUsageResponse(
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    totalDurationMs = totalDurationMs,
    loadDurationMs = loadDurationMs
)