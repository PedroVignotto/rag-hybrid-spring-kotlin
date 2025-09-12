package dev.pedro.rag.api.web.ratelimit.core

import dev.pedro.rag.api.web.ratelimit.types.RateLimitDecision
import dev.pedro.rag.config.guardrails.RateLimitProperties

internal interface RateLimiter {
    fun tryConsume(
        clientKey: String,
        endpointKey: String,
        rule: RateLimitProperties.Rule,
    ): RateLimitDecision
}
