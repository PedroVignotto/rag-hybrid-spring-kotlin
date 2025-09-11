package dev.pedro.rag.config.guardrails

import jakarta.validation.constraints.Min
import org.hibernate.validator.constraints.time.DurationMin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@ConfigurationProperties("guardrails.rate-limit")
@Validated
data class RateLimitProperties(
    val enabled: Boolean,
    val emitHeaders: Boolean,
    val defaultRule: Rule,
    val overrides: Map<String, Rule> = emptyMap()
) {
    data class Rule(
        @field:Min(1)
        val capacity: Int,
        @field:Min(1)
        val refill: Int,
        @field:DurationMin(seconds = 1)
        val period: Duration
    )
}