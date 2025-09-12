package dev.pedro.rag.api.web.ratelimit.resolver

import jakarta.servlet.http.HttpServletRequest

class ClientKeyResolver {
    fun resolve(req: HttpServletRequest): String {
        val forwarded = firstForwardedIp(req.getHeader("X-Forwarded-For"))
        return forwarded ?: (req.remoteAddr ?: "unknown")
    }

    private fun firstForwardedIp(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val first = header.split(',').firstOrNull()?.trim()
        return first?.takeIf { it.isNotEmpty() }
    }
}