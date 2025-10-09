package dev.pedro.rag.application.i18n

import java.util.Locale

fun resolvePtBrOrFallback(
    lang: String?,
    fallback: Locale = Locale.ENGLISH,
): Locale {
    val tag = (lang ?: "").trim().replace('_', '-').lowercase()
    return if (tag == "pt" || tag.startsWith("pt-br")) Locale.forLanguageTag("pt-BR") else fallback
}
