package dev.pedro.rag.application.retrieval.ask.i18n

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.support.StaticMessageSource
import java.util.Locale

class DefaultAskLocalizationTest {
    private val sut = DefaultAskLocalization(baseMessageSource())

    @Test
    fun `should resolve pt-BR when lang is pt-BR`() {
        val text = sut.noContext("pt-BR")
        assertThat(text).isEqualTo("PT-SEM-CONTEXTO")
    }

    @Test
    fun `should fallback to EN when pt-BR key is missing`() {
        val sms =
            StaticMessageSource().apply {
                addMessage("ask.noContext", Locale.ENGLISH, "EN-NO-CONTEXT")
            }
        val customSut = DefaultAskLocalization(sms)

        val text = customSut.noContext("pt-BR")
        assertThat(text).isEqualTo("EN-NO-CONTEXT")
    }

    @Test
    fun `should map pt variants to pt-BR`() {
        assertThat(sut.noContext("pt")).isEqualTo("PT-SEM-CONTEXTO")
        assertThat(sut.noContext("pt_br")).isEqualTo("PT-SEM-CONTEXTO")
        assertThat(sut.noContext("PT-BR")).isEqualTo("PT-SEM-CONTEXTO")
    }

    @Test
    fun `should fallback to EN when lang is null or unknown`() {
        assertThat(sut.noContext(null)).isEqualTo("EN-NO-CONTEXT")
        assertThat(sut.noContext("fr")).isEqualTo("EN-NO-CONTEXT")
    }

    @Test
    fun `should return key itself when missing in EN too`() {
        val sms = StaticMessageSource().apply {}
        val customSut = DefaultAskLocalization(sms)

        val text = customSut.noContext("en")
        assertThat(text).isEqualTo("ask.noContext")
    }

    private fun baseMessageSource(): StaticMessageSource {
        val sms = StaticMessageSource()
        val en = Locale.ENGLISH
        val ptBR = Locale.forLanguageTag("pt-BR")
        sms.addMessage("ask.noContext", en, "EN-NO-CONTEXT")
        sms.addMessage("ask.noContext", ptBR, "PT-SEM-CONTEXTO")
        return sms
    }
}
