package dev.pedro.rag.api.web.ratelimit.types

data class RateLimitDecision(
    val allowed: Boolean,
    val retryAfterSeconds: Long?,
)
