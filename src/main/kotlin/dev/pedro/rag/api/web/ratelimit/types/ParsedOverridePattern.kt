package dev.pedro.rag.api.web.ratelimit.types

import dev.pedro.rag.config.guardrails.RateLimitProperties
import org.springframework.web.util.pattern.PathPattern

data class ParsedOverridePattern(
    val key: String,
    val pattern: PathPattern,
    val rule: RateLimitProperties.Rule,
)
