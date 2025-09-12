package dev.pedro.rag.api.web.ratelimit.core

import dev.pedro.rag.api.web.ratelimit.types.RateLimitDecision
import dev.pedro.rag.config.guardrails.RateLimitProperties
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.TimeMeter
import java.util.concurrent.ConcurrentHashMap

internal class Bucket4jRateLimiter(
    private val timeMeter: TimeMeter = TimeMeter.SYSTEM_MILLISECONDS,
) : RateLimiter {
    private companion object {
        private const val KEY_DELIMITER: Char = '|'
        private const val NANOS_PER_SECOND: Long = 1_000_000_000L
    }

    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun tryConsume(
        clientKey: String,
        endpointKey: String,
        rule: RateLimitProperties.Rule,
    ): RateLimitDecision {
        val probe =
            buckets
                .computeIfAbsent(keyOf(clientKey, endpointKey)) { newBucket(rule) }
                .tryConsumeAndReturnRemaining(1)
        return RateLimitDecision(
            allowed = probe.isConsumed,
            retryAfterSeconds =
                probe.nanosToWaitForRefill
                    .takeUnless { probe.isConsumed }
                    ?.let(::nanosToCeilSeconds),
        )
    }

    private fun keyOf(
        clientKey: String,
        endpointKey: String,
    ): String = "$clientKey$KEY_DELIMITER$endpointKey"

    private fun newBucket(rule: RateLimitProperties.Rule): Bucket =
        Bucket.builder()
            .withCustomTimePrecision(timeMeter)
            .addLimit(rule.toBandwidth())
            .build()

    private fun RateLimitProperties.Rule.toBandwidth(): Bandwidth =
        Bandwidth.builder()
            .capacity(capacity.toLong())
            .refillIntervally(refill.toLong(), period)
            .build()

    private fun nanosToCeilSeconds(nanos: Long): Long = Math.ceilDiv(nanos, NANOS_PER_SECOND)
}
