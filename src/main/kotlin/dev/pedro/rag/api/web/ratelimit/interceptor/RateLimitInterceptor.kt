package dev.pedro.rag.api.web.ratelimit.interceptor

import dev.pedro.rag.api.web.ratelimit.core.RateLimiter
import dev.pedro.rag.api.web.ratelimit.resolver.ClientKeyResolver
import dev.pedro.rag.api.web.ratelimit.resolver.EndpointRuleResolver
import dev.pedro.rag.api.web.ratelimit.types.RateLimitDecision
import dev.pedro.rag.config.guardrails.RateLimitProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.HandlerInterceptor

class RateLimitInterceptor(
    private val properties: RateLimitProperties,
    private val clientKeyResolver: ClientKeyResolver,
    private val endpointRuleResolver: EndpointRuleResolver,
    private val rateLimiter: RateLimiter,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean =
        if (!properties.enabled) {
            true
        } else {
            run {
                val clientKey = clientKeyResolver.resolve(request)
                val (endpointKey, rule) = endpointRuleResolver.resolve(request).let { it.endpointKey to it.rule }
                rateLimiter.tryConsume(clientKey, endpointKey, rule)
                    .also { emitHeadersIfConfigured(response, clientKey, endpointKey, rule, it) }
                    .let { decision ->
                        if (decision.allowed) {
                            true
                        } else {
                            applyTooManyRequests(response, decision)
                            false
                        }
                    }
            }
        }

    private fun emitHeadersIfConfigured(
        response: HttpServletResponse,
        clientKey: String,
        endpointKey: String,
        rule: RateLimitProperties.Rule,
        decision: RateLimitDecision,
    ) {
        if (!properties.emitHeaders) return
        response.setHeader("X-RateLimit-Endpoint", endpointKey)
        response.setHeader("X-RateLimit-Client", clientKey)
        response.setHeader("X-RateLimit-Limit", rule.capacity.toString())
        response.setHeader("X-RateLimit-Refill", rule.refill.toString())
        response.setHeader("X-RateLimit-Period-Seconds", rule.period.seconds.toString())
        decision.retryAfterSeconds?.let {
            response.setHeader("X-RateLimit-Retry-After-Seconds", it.toString())
        }
    }

    private fun applyTooManyRequests(
        response: HttpServletResponse,
        decision: RateLimitDecision,
    ) {
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        decision.retryAfterSeconds?.let { response.setHeader(HttpHeaders.RETRY_AFTER, it.toString()) }
    }
}
