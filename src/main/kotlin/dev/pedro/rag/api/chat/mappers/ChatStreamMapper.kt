package dev.pedro.rag.api.chat.mappers

import dev.pedro.rag.api.chat.response.stream.ChatUsageResponse
import dev.pedro.rag.domain.chat.ChatUsage

fun ChatUsage.toResponse() =
    ChatUsageResponse(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalDurationMs = totalDurationMs,
        loadDurationMs = loadDurationMs,
    )
