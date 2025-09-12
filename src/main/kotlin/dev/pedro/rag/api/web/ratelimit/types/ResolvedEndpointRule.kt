package dev.pedro.rag.api.web.ratelimit.types

import dev.pedro.rag.config.guardrails.RateLimitProperties

data class ResolvedEndpointRule(
    val endpointKey: String,
    val rule: RateLimitProperties.Rule,
)
