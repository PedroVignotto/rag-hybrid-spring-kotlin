package dev.pedro.rag.application.retrieval.ask.i18n

import dev.pedro.rag.application.i18n.msgOrFallback
import dev.pedro.rag.application.i18n.resolvePtBrOrFallback
import org.springframework.context.MessageSource
import java.util.Locale

internal class DefaultAskLocalization(
    private val messageSource: MessageSource,
    private val fallbackLocale: Locale = Locale.ENGLISH,
) : AskLocalization {
    override fun noContext(lang: String?): String {
        val locale = resolvePtBrOrFallback(lang, fallbackLocale)
        return messageSource.msgOrFallback("ask.noContext", locale, fallbackLocale)
    }
}
