package dev.pedro.rag.api.web.ratelimit.types

internal data class RateLimitDecision(
    val allowed: Boolean,
    val retryAfterSeconds: Long?,
)
