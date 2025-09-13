package dev.pedro.rag.api.web.ratelimit.resolver

import dev.pedro.rag.api.web.ratelimit.types.ResolvedEndpointRule
import dev.pedro.rag.config.guardrails.RateLimitProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.pattern.PathPattern

class EndpointRuleResolver(
    private val props: RateLimitProperties,
) {
    private data class Entry(
        val rawKey: String,
        val canonical: String,
        val rule: RateLimitProperties.Rule,
    )

    private val entries: List<Entry> =
        props.overrides.entries.map { (k, v) -> Entry(rawKey = k, canonical = canonical(k), rule = v) }
    private val exactByCanonical: Map<String, Entry> = entries.associateBy { it.canonical }

    fun resolve(req: HttpServletRequest): ResolvedEndpointRule {
        val matched = matchedPatternOf(req)
        val uri = req.requestURI ?: "/"
        matched?.let { key ->
            exactByCanonical[canonical(key)]?.let { hit ->
                return ResolvedEndpointRule(endpointKey = key, rule = hit.rule)
            }
        }
        exactByCanonical[canonical(uri)]?.let { hit ->
            return ResolvedEndpointRule(endpointKey = uri, rule = hit.rule)
        }
        return ResolvedEndpointRule(endpointKey = matched ?: uri, rule = props.defaultRule)
    }

    private fun matchedPatternOf(req: HttpServletRequest): String? =
        when (val attr = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)) {
            is PathPattern -> attr.patternString
            is String -> attr
            else -> null
        }

    private fun canonical(s: String): String = s.filter { it.isLetterOrDigit() }.lowercase()
}
