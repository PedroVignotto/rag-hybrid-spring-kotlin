package dev.pedro.rag.application.i18n

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.support.StaticMessageSource
import java.util.Locale

class MessageSourceExtTest {
    @Test
    fun `should return target locale message when present`() {
        val sms =
            StaticMessageSource().apply {
                addMessage("demo.key", Locale.ENGLISH, "EN-VALUE")
                addMessage("demo.key", Locale.forLanguageTag("pt-BR"), "PT-VALUE")
            }

        val out = sms.msgOrFallback("demo.key", Locale.forLanguageTag("pt-BR"))

        assertThat(out).isEqualTo("PT-VALUE")
    }

    @Test
    fun `should fallback to EN when target key is missing`() {
        val sms =
            StaticMessageSource().apply {
                addMessage("demo.key", Locale.ENGLISH, "EN-VALUE")
            }

        val out = sms.msgOrFallback("demo.key", Locale.forLanguageTag("pt-BR"))

        assertThat(out).isEqualTo("EN-VALUE")
    }

    @Test
    fun `should return key itself when missing in both target and fallback`() {
        val sms = StaticMessageSource()

        val out = sms.msgOrFallback("demo.key", Locale.forLanguageTag("pt-BR"))

        assertThat(out).isEqualTo("demo.key")
    }

    @Test
    fun `should return EN when target is EN`() {
        val sms =
            StaticMessageSource().apply {
                addMessage("demo.key", Locale.ENGLISH, "EN-VALUE")
            }

        val out = sms.msgOrFallback("demo.key", Locale.ENGLISH)

        assertThat(out).isEqualTo("EN-VALUE")
    }
}
