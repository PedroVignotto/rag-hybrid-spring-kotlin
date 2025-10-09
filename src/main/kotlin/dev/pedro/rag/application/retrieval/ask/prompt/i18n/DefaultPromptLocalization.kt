package dev.pedro.rag.application.retrieval.ask.prompt.i18n

import dev.pedro.rag.application.i18n.msgOrFallback
import dev.pedro.rag.application.i18n.resolvePtBrOrFallback
import org.springframework.context.MessageSource
import java.util.Locale

internal class DefaultPromptLocalization(
    private val messageSource: MessageSource,
    private val fallbackLocale: Locale = Locale.ENGLISH,
) : PromptLocalization {
    override fun labels(lang: String?): PromptLabels {
        val locale = resolvePtBrOrFallback(lang, fallbackLocale)
        val msg: (String) -> String = { key -> messageSource.msgOrFallback(key, locale, fallbackLocale) }
        return PromptLabels(
            systemHeader = msg("prompt.system.header"),
            ruleUseOnlyContext = msg("prompt.rule.useOnlyContext"),
            ruleCiteAllWithN = msg("prompt.rule.citeAll"),
            ruleAdmitUnknown = msg("prompt.rule.admitUnknown"),
            ruleOutputFormat = msg("prompt.rule.outputFormat"),
            context = msg("prompt.label.context"),
            referenceIndex = msg("prompt.label.referenceIndex"),
            question = msg("prompt.label.question"),
            responseFormatIntro = msg("prompt.response.formatIntro"),
            contextEmptyHint = msg("prompt.context.empty"),
            answerHere = msg("prompt.answer.placeholder"),
        )
    }
}
