package dev.pedro.rag.application.retrieval.ask.parsing

internal class DefaultOutputParser(
    private val allowNsFromAnswer: Boolean = false
) : OutputParser {
    companion object {
        private val ANSWER_REGEX = Regex("""(?si)ANSWER:\s*(.*?)\s*\n\s*CITATIONS:""")
        private val CITATIONS_REGEX = Regex("""(?si)CITATIONS:\s*(.*)$""")
        private val CITATION_N_REGEX = Regex("""\[(\d+)]""")
    }

    override fun parse(raw: String): ParsedOutput {
        val mAnswer = ANSWER_REGEX.find(raw)
        val answer = if (mAnswer != null) mAnswer.groupValues[1].trim() else raw.trim()
        val mCits = CITATIONS_REGEX.find(raw)
        val citationsBlock = if (mCits != null) mCits.groupValues[1] else ""
        var ns = extractNs(citationsBlock)
        if (ns.isEmpty() && allowNsFromAnswer) {
            ns = extractNs(raw)
        }
        return ParsedOutput(answer = answer, citationNs = ns)
    }

    private fun extractNs(text: String): List<Int> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<Int>()
        for (m in CITATION_N_REGEX.findAll(text)) {
            val n = m.groupValues[1].toInt()
            if (!out.contains(n)) out.add(n)
        }
        return out
    }
}
