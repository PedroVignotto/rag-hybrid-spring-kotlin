package dev.pedro.rag.api.web.ratelimit.interceptor

import dev.pedro.rag.api.web.ratelimit.core.RateLimiter
import dev.pedro.rag.api.web.ratelimit.resolver.ClientKeyResolver
import dev.pedro.rag.api.web.ratelimit.resolver.EndpointRuleResolver
import dev.pedro.rag.api.web.ratelimit.types.RateLimitDecision
import dev.pedro.rag.config.guardrails.RateLimitProperties
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.*
import java.time.Duration

class RateLimitInterceptorTest {

    private lateinit var properties: RateLimitProperties
    private lateinit var clientKeyResolver: ClientKeyResolver
    private lateinit var endpointRuleResolver: EndpointRuleResolver
    private lateinit var rateLimiter: RateLimiter
    private lateinit var interceptor: RateLimitInterceptor
    private lateinit var mvc: MockMvc

    @RestController
    @RequestMapping("/v1")
    private class DummyController {
        @PostMapping("/chat")
        fun chat(): ResponseEntity<Void> = ResponseEntity.ok().build()

        @GetMapping("/other/{id}")
        fun other(@PathVariable id: String): ResponseEntity<Void> = ResponseEntity.ok().build()
    }

    @BeforeEach
    fun setup() {
        properties = RateLimitProperties(
            enabled = true,
            emitHeaders = true,
            defaultRule = rule(capacity = 10, refill = 10, periodSeconds = 10),
            overrides = mapOf(
                "/v1/chat" to rule(capacity = 3, refill = 3, periodSeconds = 30),
                "/v1/**"   to rule(capacity = 5, refill = 5, periodSeconds = 60)
            )
        )
        clientKeyResolver = ClientKeyResolver()
        endpointRuleResolver = EndpointRuleResolver(properties)
        rateLimiter = mockk()
        interceptor = RateLimitInterceptor(
            properties,
            clientKeyResolver,
            endpointRuleResolver,
            rateLimiter
        )
        mvc = MockMvcBuilders
            .standaloneSetup(DummyController())
            .addInterceptors(interceptor)
            .build()
    }

    @Test
    fun `should allow request and emit headers when enabled`() {
        every { rateLimiter.tryConsume(any(), any(), any()) } returns RateLimitDecision(true, null)

        mvc.perform(
            post("/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "1.2.3.4")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("X-RateLimit-Endpoint", "/v1/chat"))
            .andExpect(header().string("X-RateLimit-Client", "1.2.3.4"))
            .andExpect(header().string("X-RateLimit-Limit", "3"))
            .andExpect(header().string("X-RateLimit-Refill", "3"))
            .andExpect(header().string("X-RateLimit-Period-Seconds", "30"))

        verify {
            rateLimiter.tryConsume("1.2.3.4", "/v1/chat", properties.overrides["/v1/chat"]!!)
        }
    }

    @Test
    fun `should deny with 429 and Retry-After when limit exceeded`() {
        every { rateLimiter.tryConsume(any(), any(), any()) } returns RateLimitDecision(false, 5)

        mvc.perform(
            post("/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "9.9.9.9")
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().string("Retry-After", "5"))
    }

    @Test
    fun `should resolve wildcard override when no exact match`() {
        every { rateLimiter.tryConsume(any(), any(), any()) } returns RateLimitDecision(true, null)

        mvc.perform(
            get("/v1/other/123")
                .header("X-Forwarded-For", "7.7.7.7")
        )
            .andExpect(status().isOk)

        verify {
            rateLimiter.tryConsume("7.7.7.7", "/v1/**", properties.overrides["/v1/**"]!!)
        }
    }

    @Test
    fun `should bypass when disabled (do not call rate limiter)`() {
        val disabledProps = properties.copy(enabled = false)
        val interceptorDisabled = RateLimitInterceptor(
            disabledProps, clientKeyResolver, endpointRuleResolver, rateLimiter
        )
        val mvcDisabled = MockMvcBuilders
            .standaloneSetup(DummyController())
            .addInterceptors(interceptorDisabled)
            .build()

        every { rateLimiter.tryConsume(any(), any(), any()) } returns RateLimitDecision(false, 5)

        mvcDisabled.perform(
            post("/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "1.2.3.4")
        ).andExpect(status().isOk)

        verify(exactly = 0) { rateLimiter.tryConsume(any(), any(), any()) }
    }

    @Test
    fun `should not emit headers when emitHeaders is false`() {
        val noHeadersProps = properties.copy(emitHeaders = false)
        val interceptorNoHeaders = RateLimitInterceptor(
            noHeadersProps, clientKeyResolver, endpointRuleResolver, rateLimiter
        )
        val mvcNoHeaders = MockMvcBuilders
            .standaloneSetup(DummyController())
            .addInterceptors(interceptorNoHeaders)
            .build()
        every { rateLimiter.tryConsume(any(), any(), any()) } returns RateLimitDecision(true, null)

        mvcNoHeaders.perform(
            post("/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "5.5.5.5")
        )
            .andExpect(status().isOk)
            .andExpect(header().doesNotExist("X-RateLimit-Endpoint"))
            .andExpect(header().doesNotExist("X-RateLimit-Client"))
            .andExpect(header().doesNotExist("X-RateLimit-Limit"))
            .andExpect(header().doesNotExist("X-RateLimit-Refill"))
            .andExpect(header().doesNotExist("X-RateLimit-Period-Seconds"))
            .andExpect(header().doesNotExist("X-RateLimit-Retry-After-Seconds"))
    }

    @Test
    fun `should return 429 without Retry-After when denied and retryAfter is null`() {
        every { rateLimiter.tryConsume(any(), any(), any()) } returns RateLimitDecision(false, null)

        mvc.perform(
            post("/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "8.8.8.8")
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().doesNotExist("Retry-After"))
            .andExpect(header().doesNotExist("X-RateLimit-Retry-After-Seconds"))
    }

    private fun rule(capacity: Int, refill: Int, periodSeconds: Long) =
        RateLimitProperties.Rule(
            capacity = capacity,
            refill = refill,
            period = Duration.ofSeconds(periodSeconds)
        )
}