package dev.pedro.rag.api.web.ratelimit.resolver

import dev.pedro.rag.config.guardrails.RateLimitProperties
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.pattern.PathPatternParser
import java.time.Duration

class EndpointRuleResolverTest {
    private val props =
        RateLimitProperties(
            enabled = true,
            emitHeaders = true,
            defaultRule = rule(10, 10, 10),
            overrides =
                mapOf(
                    "/v1/chat/stream" to rule(capacity = 3, refill = 3, periodSec = 30),
                    "/users/**" to rule(capacity = 5, refill = 5, periodSec = 10),
                    "/users/admin/**" to rule(capacity = 1, refill = 1, periodSec = 1),
                ),
        )

    @Test
    fun `should resolve exact override by matched pattern`() {
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/v1/chat/stream"
                setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/chat/stream")
            }
        val sut = EndpointRuleResolver(props)

        val result = sut.resolve(request)

        Assertions.assertThat(result.endpointKey).isEqualTo("/v1/chat/stream")
        Assertions.assertThat(result.rule.capacity).isEqualTo(3)
        Assertions.assertThat(result.rule.period).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `should resolve wildcard override when no exact override exists`() {
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/users/42"
                setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/users/{id}")
            }
        val sut = EndpointRuleResolver(props)

        val result = sut.resolve(request)

        Assertions.assertThat(result.endpointKey).isEqualTo("/users/**")
        Assertions.assertThat(result.rule.capacity).isEqualTo(5)
        Assertions.assertThat(result.rule.period).isEqualTo(Duration.ofSeconds(10))
    }

    @Test
    fun `should pick most specific wildcard when multiple match`() {
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/users/admin/ops"
                setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/users/{segment}/{rest}")
            }
        val sut = EndpointRuleResolver(props)

        val result = sut.resolve(request)

        Assertions.assertThat(result.endpointKey).isEqualTo("/users/admin/**")
        Assertions.assertThat(result.rule.capacity).isEqualTo(1)
        Assertions.assertThat(result.rule.period).isEqualTo(Duration.ofSeconds(1))
    }

    @Test
    fun `should fallback to default when no override matches`() {
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/v1/embeddings"
                setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/embeddings")
            }
        val sut = EndpointRuleResolver(props)

        val result = sut.resolve(request)

        Assertions.assertThat(result.endpointKey).isEqualTo("/v1/embeddings")
        Assertions.assertThat(result.rule.capacity).isEqualTo(10)
        Assertions.assertThat(result.rule.period).isEqualTo(Duration.ofSeconds(10))
    }

    @Test
    fun `should fallback to requestURI when pattern attribute is absent`() {
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/no/attr/here"
            }
        val sut = EndpointRuleResolver(props)

        val result = sut.resolve(request)

        Assertions.assertThat(result.endpointKey).isEqualTo("/no/attr/here")
        Assertions.assertThat(result.rule.capacity).isEqualTo(10)
    }

    @Test
    fun `should accept PathPattern attribute as matched pattern`() {
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/users/777"
                setAttribute(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                    PathPatternParser().parse("/users/{id}"),
                )
            }
        val sut = EndpointRuleResolver(props)

        val result = sut.resolve(request)

        Assertions.assertThat(result.endpointKey).isEqualTo("/users/**")
        Assertions.assertThat(result.rule.capacity).isEqualTo(5)
    }

    @Test
    fun `should fallback to slash when requestURI is null for wildcard check`() {
        val props =
            RateLimitProperties(
                enabled = true,
                emitHeaders = true,
                defaultRule = rule(10, 10, 10),
                overrides = mapOf("/**" to rule(2, 2, 1)),
            )
        val request = mockk<HttpServletRequest>()
        every { request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) } returns "/no-override"
        every { request.requestURI } returns null
        val sut = EndpointRuleResolver(props)

        val result = sut.resolve(request)

        Assertions.assertThat(result.endpointKey).isEqualTo("/**")
        Assertions.assertThat(result.rule.capacity).isEqualTo(2)
    }

    @Test
    fun `should use endpointKey unknown when both matched pattern and requestURI are absent`() {
        val props =
            RateLimitProperties(
                enabled = true,
                emitHeaders = true,
                defaultRule = rule(10, 10, 10),
                overrides = emptyMap(),
            )
        val request = mockk<HttpServletRequest>()
        every { request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) } returns null
        every { request.requestURI } returns null
        val sut = EndpointRuleResolver(props)

        val result = sut.resolve(request)

        Assertions.assertThat(result.endpointKey).isEqualTo("unknown")
        Assertions.assertThat(result.rule.capacity).isEqualTo(10)
    }

    private fun rule(
        capacity: Int,
        refill: Int,
        periodSec: Long,
    ) = RateLimitProperties.Rule(capacity = capacity, refill = refill, period = Duration.ofSeconds(periodSec))
}
