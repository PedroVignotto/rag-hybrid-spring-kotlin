package dev.pedro.rag.application.retrieval.ask.prompt.i18n

import org.springframework.context.MessageSource
import java.util.Locale

internal class DefaultPromptLocalization(
    private val messageSource: MessageSource,
    private val fallbackLocale: Locale = Locale.ENGLISH,
) : PromptLocalization {
    override fun labels(lang: String?): PromptLabels {
        val locale = resolveLocale(lang)
        return PromptLabels(
            systemHeader = messageOrEnglishFallback("prompt.system.header", locale),
            ruleUseOnlyContext = messageOrEnglishFallback("prompt.rule.useOnlyContext", locale),
            ruleCiteAllWithN = messageOrEnglishFallback("prompt.rule.citeAll", locale),
            ruleAdmitUnknown = messageOrEnglishFallback("prompt.rule.admitUnknown", locale),
            ruleOutputFormat = messageOrEnglishFallback("prompt.rule.outputFormat", locale),
            context = messageOrEnglishFallback("prompt.label.context", locale),
            referenceIndex = messageOrEnglishFallback("prompt.label.referenceIndex", locale),
            question = messageOrEnglishFallback("prompt.label.question", locale),
            responseFormatIntro = messageOrEnglishFallback("prompt.response.formatIntro", locale),
            contextEmptyHint = messageOrEnglishFallback("prompt.context.empty", locale),
            answerHere = messageOrEnglishFallback("prompt.answer.placeholder", locale),
        )
    }

    private fun resolveLocale(lang: String?): Locale {
        val tag = lang?.trim()?.replace('_', '-')?.lowercase()
        return when {
            tag.isNullOrEmpty() -> fallbackLocale
            tag == "pt" || tag.startsWith("pt-br") -> Locale.forLanguageTag("pt-BR")
            else -> fallbackLocale
        }
    }

    private fun messageOrEnglishFallback(
        key: String,
        locale: Locale,
    ): String {
        val english = messageSource.getMessage(key, null, key, Locale.ENGLISH) ?: key
        return messageSource.getMessage(key, null, english, locale) ?: english
    }
}
