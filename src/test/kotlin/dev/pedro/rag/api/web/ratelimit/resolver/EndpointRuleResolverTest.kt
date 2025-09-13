package dev.pedro.rag.api.web.ratelimit.resolver

import dev.pedro.rag.config.guardrails.RateLimitProperties
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.pattern.PathPatternParser
import java.time.Duration

class EndpointRuleResolverTest {
    @Test
    fun `should resolve exact override by matched pattern (YAML-style key with slashes)`() {
        val sut =
            EndpointRuleResolver(
                props(overrides = mapOf("/v1/chat" to rule(1, 1, 30))),
            )
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/v1/chat"
                setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/chat")
            }

        val result = sut.resolve(request)

        assertThat(result.endpointKey).isEqualTo("/v1/chat")
        assertThat(result.rule.capacity).isEqualTo(1)
        assertThat(result.rule.period).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `should resolve exact override by requestURI when matched pattern is templated`() {
        val sut =
            EndpointRuleResolver(
                props(overrides = mapOf("/users/42" to rule(2, 2, 20))),
            )
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/users/42"
                setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/users/{id}")
            }

        val result = sut.resolve(request)

        assertThat(result.endpointKey).isEqualTo("/users/42")
        assertThat(result.rule.capacity).isEqualTo(2)
        assertThat(result.rule.period).isEqualTo(Duration.ofSeconds(20))
    }

    @Test
    fun `should resolve when overrides keys are already canonical (as in configprops)`() {
        val sut =
            EndpointRuleResolver(
                props(overrides = mapOf("v1chat" to rule(1, 1, 30))),
            )
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/v1/chat"
                setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/chat")
            }

        val result = sut.resolve(request)

        assertThat(result.endpointKey).isEqualTo("/v1/chat")
        assertThat(result.rule.capacity).isEqualTo(1)
        assertThat(result.rule.period).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `should fallback to default when no override matches`() {
        val sut =
            EndpointRuleResolver(
                props(defaultRule = rule(10, 10, 10), overrides = emptyMap()),
            )
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/v1/embeddings"
                setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/embeddings")
            }

        val result = sut.resolve(request)

        assertThat(result.endpointKey).isEqualTo("/v1/embeddings")
        assertThat(result.rule.capacity).isEqualTo(10)
        assertThat(result.rule.period).isEqualTo(Duration.ofSeconds(10))
    }

    @Test
    fun `should accept PathPattern attribute as matched pattern`() {
        val sut =
            EndpointRuleResolver(
                props(overrides = mapOf("/v1/chat" to rule(1, 1, 30))),
            )
        val request =
            MockHttpServletRequest().apply {
                requestURI = "/v1/chat"
                setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, PathPatternParser().parse("/v1/chat"))
            }

        val result = sut.resolve(request)

        assertThat(result.endpointKey).isEqualTo("/v1/chat")
        assertThat(result.rule.capacity).isEqualTo(1)
    }

    @Test
    fun `should fallback to default with requestURI when matched pattern attribute is absent`() {
        val sut =
            EndpointRuleResolver(
                props(defaultRule = rule(10, 10, 10), overrides = emptyMap()),
            )
        val request = MockHttpServletRequest().apply { requestURI = "/no/attr/here" }

        val result = sut.resolve(request)

        assertThat(result.endpointKey).isEqualTo("/no/attr/here")
        assertThat(result.rule.capacity).isEqualTo(10)
    }

    @Test
    fun `should fallback to default with slash when requestURI is null`() {
        val sut =
            EndpointRuleResolver(
                props(defaultRule = rule(10, 10, 10), overrides = emptyMap()),
            )
        val request = mockk<HttpServletRequest>()
        every { request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) } returns null
        every { request.requestURI } returns null

        val result = sut.resolve(request)

        assertThat(result.endpointKey).isEqualTo("/")
        assertThat(result.rule.capacity).isEqualTo(10)
    }

    private fun rule(
        capacity: Int,
        refill: Int,
        periodSeconds: Long,
    ) = RateLimitProperties.Rule(capacity, refill, Duration.ofSeconds(periodSeconds))

    private fun props(
        enabled: Boolean = true,
        emitHeaders: Boolean = true,
        defaultRule: RateLimitProperties.Rule = rule(10, 10, 10),
        overrides: Map<String, RateLimitProperties.Rule> = emptyMap(),
    ) = RateLimitProperties(
        enabled = enabled,
        emitHeaders = emitHeaders,
        defaultRule = defaultRule,
        overrides = overrides,
    )
}
