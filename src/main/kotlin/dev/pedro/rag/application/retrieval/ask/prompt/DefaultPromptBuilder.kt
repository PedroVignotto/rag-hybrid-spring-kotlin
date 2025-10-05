package dev.pedro.rag.application.retrieval.ask.prompt

import dev.pedro.rag.application.retrieval.ask.context.BuiltContext
import dev.pedro.rag.application.retrieval.ask.prompt.i18n.PromptLocalization

internal class DefaultPromptBuilder(
    private val localization: PromptLocalization,
    private val requireCitations: Boolean = true,
    private val requireAdmitUnknown: Boolean = true,
    private val autoDetectLang: Boolean = true,
) : PromptBuilder {
    companion object {
        private val PT_HINTS =
            setOf(
                "que horas", "pix", "promoção", "promo", "entrega", "taxa",
                "domingo", "horário", "qual", "tem", "aceita",
            )
        private val DIACRITICS_PT = setOf("ã", "õ", "ç", "é", "ê", "á", "í", "ó", "ú")
    }

    override fun build(
        context: BuiltContext,
        query: String,
        lang: String?,
    ): PromptPayload {
        val targetLang = resolveLang(lang, query)
        val labels = localization.labels(targetLang)
        val system =
            buildString {
                appendLine(labels.systemHeader)
                appendLine()
                appendLine("1) ${labels.ruleUseOnlyContext}")
                if (requireCitations) appendLine("2) ${labels.ruleCiteAllWithN}")
                if (requireAdmitUnknown) appendLine("3) ${labels.ruleAdmitUnknown}")
                appendLine("4) ${labels.ruleOutputFormat}")
            }
        val user =
            buildString {
                appendLine("${labels.context}:")
                if (context.text.isBlank()) {
                    appendLine(labels.contextEmptyHint)
                } else {
                    appendLine(context.text.trim())
                }
                if (context.index.isNotEmpty()) {
                    appendLine()
                    appendLine("${labels.referenceIndex}:")
                    context.index.forEach { ci ->
                        appendLine("[${ci.n}] ${ci.documentId}#chunk${ci.chunkIndex} — ${ci.title}")
                    }
                }
                appendLine()
                appendLine("${labels.question}:")
                appendLine(query.trim())
                appendLine()
                appendLine(labels.responseFormatIntro)
                appendLine("ANSWER:")
                appendLine("<${labels.answerHere}>")
                appendLine()
                appendLine("CITATIONS:")
                appendLine("[1] <documentId>#chunk<idx>")
                appendLine("[2] ...")
            }
        return PromptPayload(system = system.trimEnd(), user = user.trimEnd())
    }

    private fun resolveLang(
        explicitLang: String?,
        query: String,
    ): String {
        val explicit = explicitLang?.trim()
        if (!explicit.isNullOrEmpty()) return explicit
        if (!autoDetectLang) return "en"
        val q = query.lowercase()
        return if (PT_HINTS.any { it in q } || DIACRITICS_PT.any { it in q }) "pt-BR" else "en"
    }
}
