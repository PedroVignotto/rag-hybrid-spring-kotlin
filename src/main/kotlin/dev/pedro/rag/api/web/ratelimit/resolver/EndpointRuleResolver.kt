package dev.pedro.rag.api.web.ratelimit.resolver

import dev.pedro.rag.api.web.ratelimit.types.ParsedOverridePattern
import dev.pedro.rag.api.web.ratelimit.types.ResolvedEndpointRule
import dev.pedro.rag.config.guardrails.RateLimitProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.server.PathContainer
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.pattern.PathPattern
import org.springframework.web.util.pattern.PathPatternParser

internal class EndpointRuleResolver(
    private val props: RateLimitProperties,
) {
    private val parser = PathPatternParser()
    private val exactOverrides: Map<String, RateLimitProperties.Rule> =
        props.overrides.filterKeys { it.indexOf('*') < 0 }
    private val wildcardOverrides: List<ParsedOverridePattern> =
        props.overrides
            .filterKeys { it.indexOf('*') >= 0 }
            .map { (key, rule) -> ParsedOverridePattern(key, parser.parse(key), rule) }

    internal fun resolve(req: HttpServletRequest): ResolvedEndpointRule {
        val matchedPattern = matchedPatternOf(req)
        exactOverrides[matchedPattern]?.let { rule ->
            return ResolvedEndpointRule(endpointKey = matchedPattern, rule = rule)
        }
        val uri = req.requestURI ?: "/"
        val path = PathContainer.parsePath(uri)
        val bestWildcard =
            wildcardOverrides
                .asSequence()
                .filter { it.pattern.matches(path) }
                .maxByOrNull { specificityScore(it.key) }
        if (bestWildcard != null) {
            return ResolvedEndpointRule(endpointKey = bestWildcard.key, rule = bestWildcard.rule)
        }
        return ResolvedEndpointRule(endpointKey = matchedPattern, rule = props.defaultRule)
    }

    private fun matchedPatternOf(req: HttpServletRequest): String =
        when (val attr = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)) {
            is PathPattern -> attr.patternString
            is String -> attr
            else -> req.requestURI ?: "unknown"
        }

    private fun specificityScore(key: String): Int = key.length
}