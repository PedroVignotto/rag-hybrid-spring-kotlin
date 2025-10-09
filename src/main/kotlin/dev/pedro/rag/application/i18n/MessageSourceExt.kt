package dev.pedro.rag.application.i18n

import org.springframework.context.MessageSource
import java.util.Locale

internal fun MessageSource.msgOrFallback(
    key: String,
    target: Locale,
    fallback: Locale = Locale.ENGLISH,
): String {
    val fallbackMessage = getMessage(key, null, key, fallback) ?: key
    return getMessage(key, null, fallbackMessage, target) ?: fallbackMessage
}
