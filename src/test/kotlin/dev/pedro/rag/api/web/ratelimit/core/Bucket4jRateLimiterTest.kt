package dev.pedro.rag.api.web.ratelimit.core

import dev.pedro.rag.api.web.ratelimit.types.RateLimitDecision
import dev.pedro.rag.config.guardrails.RateLimitProperties
import io.github.bucket4j.TimeMeter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class Bucket4jRateLimiterTest {
    private companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L
    }

    private class FakeTimeMeter(var nowNanos: Long = 0L) : TimeMeter {
        override fun currentTimeNanos(): Long = nowNanos

        override fun isWallClockBased(): Boolean = false

        fun advanceSeconds(seconds: Long) {
            nowNanos += seconds * NANOS_PER_SECOND
        }
    }

    @Test
    fun `should allow up to capacity and then deny`() {
        val timeMeter = FakeTimeMeter()
        val rateLimiter = Bucket4jRateLimiter(timeMeter)
        val rateLimitRule = rule(capacity = 2, refill = 1, periodSeconds = 10)

        assertThat(rateLimiter.tryConsume("clientA", "endpointX", rateLimitRule))
            .isEqualTo(RateLimitDecision(allowed = true, retryAfterSeconds = null))
        assertThat(rateLimiter.tryConsume("clientA", "endpointX", rateLimitRule))
            .isEqualTo(RateLimitDecision(allowed = true, retryAfterSeconds = null))
        val denied = rateLimiter.tryConsume("clientA", "endpointX", rateLimitRule)
        assertThat(denied.allowed).isFalse()
        assertThat(denied.retryAfterSeconds).isGreaterThanOrEqualTo(1L)
    }

    @Test
    fun `should provide retryAfter equals period when empty with intervally refill`() {
        val timeMeter = FakeTimeMeter()
        val rateLimiter = Bucket4jRateLimiter(timeMeter)
        val rateLimitRule = rule(capacity = 1, refill = 1, periodSeconds = 10)

        assertThat(rateLimiter.tryConsume("clientB", "endpointY", rateLimitRule))
            .isEqualTo(RateLimitDecision(true, null))
        val denied = rateLimiter.tryConsume("clientB", "endpointY", rateLimitRule)
        assertThat(denied.allowed).isFalse()
        assertThat(denied.retryAfterSeconds).isEqualTo(10L)
    }

    @Test
    fun `should refill after advancing time`() {
        val timeMeter = FakeTimeMeter()
        val rateLimiter = Bucket4jRateLimiter(timeMeter)
        val rateLimitRule = rule(capacity = 1, refill = 1, periodSeconds = 5)

        assertThat(rateLimiter.tryConsume("clientC", "endpointZ", rateLimitRule).allowed).isTrue()
        assertThat(rateLimiter.tryConsume("clientC", "endpointZ", rateLimitRule).allowed).isFalse()
        timeMeter.advanceSeconds(5)
        val afterRefill = rateLimiter.tryConsume("clientC", "endpointZ", rateLimitRule)
        assertThat(afterRefill.allowed).isTrue()
        assertThat(afterRefill.retryAfterSeconds).isNull()
    }

    @Test
    fun `should isolate buckets by endpointKey`() {
        val timeMeter = FakeTimeMeter()
        val rateLimiter = Bucket4jRateLimiter(timeMeter)
        val rateLimitRule = rule(capacity = 1, refill = 1, periodSeconds = 60)

        assertThat(rateLimiter.tryConsume("clientD", "endpointA", rateLimitRule).allowed).isTrue()
        assertThat(rateLimiter.tryConsume("clientD", "endpointA", rateLimitRule).allowed).isFalse()
        assertThat(rateLimiter.tryConsume("clientD", "endpointB", rateLimitRule).allowed).isTrue()
    }

    @Test
    fun `should isolate buckets by clientKey`() {
        val timeMeter = FakeTimeMeter()
        val rateLimiter = Bucket4jRateLimiter(timeMeter)
        val rateLimitRule = rule(capacity = 1, refill = 1, periodSeconds = 60)
        assertThat(rateLimiter.tryConsume("clientE1", "endpointX", rateLimitRule).allowed).isTrue()
        assertThat(rateLimiter.tryConsume("clientE1", "endpointX", rateLimitRule).allowed).isFalse()
        assertThat(rateLimiter.tryConsume("clientE2", "endpointX", rateLimitRule).allowed).isTrue()
    }

    private fun rule(
        capacity: Int,
        refill: Int,
        periodSeconds: Long,
    ) = RateLimitProperties.Rule(
        capacity = capacity,
        refill = refill,
        period = Duration.ofSeconds(periodSeconds),
    )
}
