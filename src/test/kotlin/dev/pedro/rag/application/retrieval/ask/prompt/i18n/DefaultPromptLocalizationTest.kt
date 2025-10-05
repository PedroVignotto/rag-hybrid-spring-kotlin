package dev.pedro.rag.application.retrieval.ask.prompt.i18n

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.support.StaticMessageSource
import java.util.Locale

class DefaultPromptLocalizationTest {

    private val sut = DefaultPromptLocalization(baseMessageSource())

    @Test
    fun `should resolve pt-BR labels when lang is pt-BR`() {
        val labels = sut.labels("pt-BR")

        assertThat(labels.systemHeader).isEqualTo("PT-SISTEMA")
        assertThat(labels.context).isEqualTo("PT-CONTEXTO")
        assertThat(labels.referenceIndex).isEqualTo("PT-INDICE-REFERENCIA")
        assertThat(labels.question).isEqualTo("PT-PERGUNTA")
        assertThat(labels.answerHere).isEqualTo("PT-RESPOSTA-AQUI")
    }

    @Test
    fun `should fallback to EN for missing pt-BR key`() {
        val labels = sut.labels("pt-BR")

        assertThat(labels.contextEmptyHint).isEqualTo("EN-CONTEXT-EMPTY")
    }

    @Test
    fun `should map pt variants to pt-BR`() {
        assertThat(sut.labels("pt").context).isEqualTo("PT-CONTEXTO")
        assertThat(sut.labels("pt_br").context).isEqualTo("PT-CONTEXTO")
        assertThat(sut.labels("PT-BR").context).isEqualTo("PT-CONTEXTO")
    }

    @Test
    fun `should fallback to EN when lang is null or unknown`() {
        assertThat(sut.labels(null).context).isEqualTo("EN-CONTEXT")
        assertThat(sut.labels("fr").context).isEqualTo("EN-CONTEXT")
    }

    @Test
    fun `should return key itself when missing in EN too`() {
        val sms = StaticMessageSource()
        sms.addMessage("prompt.system.header", Locale.ENGLISH, "EN-SYSTEM")
        val customSut = DefaultPromptLocalization(sms)

        val labels = customSut.labels("en")

        assertThat(labels.answerHere).isEqualTo("prompt.answer.placeholder")
        assertThat(labels.systemHeader).isEqualTo("EN-SYSTEM")
    }

    private fun baseMessageSource(): StaticMessageSource {
        val sms = StaticMessageSource()
        val en = Locale.ENGLISH
        sms.addMessage("prompt.system.header",        en, "EN-SYSTEM")
        sms.addMessage("prompt.rule.useOnlyContext",  en, "EN-USE-ONLY-CONTEXT")
        sms.addMessage("prompt.rule.citeAll",         en, "EN-CITE-ALL")
        sms.addMessage("prompt.rule.admitUnknown",    en, "EN-ADMIT-UNKNOWN")
        sms.addMessage("prompt.rule.outputFormat",    en, "EN-OUTPUT-FORMAT")
        sms.addMessage("prompt.label.context",        en, "EN-CONTEXT")
        sms.addMessage("prompt.label.referenceIndex", en, "EN-REFERENCE-INDEX")
        sms.addMessage("prompt.label.question",       en, "EN-QUESTION")
        sms.addMessage("prompt.response.formatIntro", en, "EN-FORMAT-INTRO")
        sms.addMessage("prompt.context.empty",        en, "EN-CONTEXT-EMPTY")
        sms.addMessage("prompt.answer.placeholder",   en, "EN-ANSWER-HERE")
        val ptBR = Locale.forLanguageTag("pt-BR")
        sms.addMessage("prompt.system.header",        ptBR, "PT-SISTEMA")
        sms.addMessage("prompt.rule.useOnlyContext",  ptBR, "PT-USE-ONLY-CONTEXT")
        sms.addMessage("prompt.rule.citeAll",         ptBR, "PT-CITE-ALL")
        sms.addMessage("prompt.rule.admitUnknown",    ptBR, "PT-ADMIT-UNKNOWN")
        sms.addMessage("prompt.rule.outputFormat",    ptBR, "PT-OUTPUT-FORMAT")
        sms.addMessage("prompt.label.context",        ptBR, "PT-CONTEXTO")
        sms.addMessage("prompt.label.referenceIndex", ptBR, "PT-INDICE-REFERENCIA")
        sms.addMessage("prompt.label.question",       ptBR, "PT-PERGUNTA")
        sms.addMessage("prompt.response.formatIntro", ptBR, "PT-FORMATO-INTRO")
        sms.addMessage("prompt.answer.placeholder",   ptBR, "PT-RESPOSTA-AQUI")
        return sms
    }
}