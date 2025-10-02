package dev.pedro.rag.application.retrieval.ask.context

import kotlin.math.max

internal class DefaultContextBuilder : ContextBuilder {
    companion object {
        private const val DEFAULT_SB_CAPACITY = 4096
        private const val MIN_CORE_LEN = 1
        private const val ELLIPSIS = " [...]"
        private const val NEW_BLOCK_SEPARATOR = "\n\n"
        private val WS_REGEX = Regex("\\s+")
    }

    override fun build(
        sources: List<ContextSource>,
        budgetChars: Int,
    ): BuiltContext {
        require(budgetChars > 0) { "budgetChars must be > 0" }
        if (sources.isEmpty()) {
            return BuiltContext(text = "", index = emptyList(), usedK = 0, truncated = false)
        }
        val sb = StringBuilder(minOf(budgetChars, DEFAULT_SB_CAPACITY))
        val index = ArrayList<CitationIndex>()
        var nextN = 1
        var truncated = false
        for (src in sources) {
            val clean = sanitize(src.text)
            if (clean.isEmpty()) continue
            val sep = separatorFor(sb)
            val prefix = prefixFor(nextN)
            val remaining = budgetChars - sb.length
            if (remaining <= 0) {
                truncated = true
                break
            }
            val fullLen = sep.length + prefix.length + clean.length
            if (fullLen <= remaining) {
                appendFull(sb, sep, prefix, clean)
                index.add(CitationIndex(n = nextN, documentId = src.documentId, title = src.title, chunkIndex = src.chunkIndex))
                nextN += 1
                continue
            }
            val overhead = sep.length + prefix.length
            val spaceForText = remaining - overhead
            if (spaceForText <= 0) {
                truncated = true
                break
            }
            val coreLen = max(MIN_CORE_LEN, spaceForText - ELLIPSIS.length)
            val core = takeSafe(clean, coreLen)
            appendFull(sb, sep, prefix, core)
            if (overhead + core.length < remaining) sb.append(ELLIPSIS)
            index.add(CitationIndex(n = nextN, documentId = src.documentId, title = src.title, chunkIndex = src.chunkIndex))
            nextN += 1
            truncated = true
            break
        }
        return BuiltContext(
            text = sb.toString(),
            index = index,
            usedK = index.size,
            truncated = truncated,
        )
    }

    private fun sanitize(text: String): String = WS_REGEX.replace(text, " ").trim()

    private fun separatorFor(sb: StringBuilder): String = if (sb.isEmpty()) "" else NEW_BLOCK_SEPARATOR

    private fun prefixFor(n: Int): String = "[$n] "

    private fun appendFull(
        sb: StringBuilder,
        sep: String,
        prefix: String,
        body: String,
    ) {
        if (sep.isNotEmpty()) sb.append(sep)
        sb.append(prefix).append(body)
    }

    private fun takeSafe(
        text: String,
        len: Int,
    ): String {
        if (len <= 0) return ""
        if (text.length <= len) return text
        return text.take(len).trimEnd()
    }
}
