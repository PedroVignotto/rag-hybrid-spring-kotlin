package dev.pedro.rag.application.retrieval.ask.prompt

import dev.pedro.rag.application.retrieval.ask.context.BuiltContext
import dev.pedro.rag.application.retrieval.ask.context.CitationIndex
import dev.pedro.rag.application.retrieval.ask.prompt.i18n.PromptLabels
import dev.pedro.rag.application.retrieval.ask.prompt.i18n.PromptLocalization
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefaultPromptBuilderTest {
    private val localization = FakePromptLocalization()

    private val sut =
        DefaultPromptBuilder(
            localization = localization,
            requireCitations = true,
            requireAdmitUnknown = true,
            autoDetectLang = true,
        )

    @Test
    fun `should build Portuguese prompt when lang is pt-BR`() {
        val ctx =
            BuiltContext(
                text = "[1] Tem opções vegetarianas e aceita Pix.",
                index = listOf(CitationIndex(1, "ze-menu-001", "Zé - Menu", 0)),
                usedK = 1,
                truncated = false,
            )

        val payload = sut.build(ctx, query = "Tem veggie?", lang = "pt-BR")

        assertThat(payload.system).contains("Responda apenas com informações presentes no CONTEXTO.")
        assertThat(payload.user).contains("CONTEXTO:")
        assertThat(payload.user).contains("ÍNDICE DE REFERÊNCIA:")
        assertThat(payload.user).contains("PERGUNTA:")
        assertThat(payload.user).contains("ANSWER:")
        assertThat(payload.user).contains("CITATIONS:")
        assertThat(payload.user).contains("[1] ze-menu-001#chunk0 — Zé - Menu")
    }

    @Test
    fun `should autodetect Portuguese when query hints pt`() {
        val ctx = BuiltContext(text = "", index = emptyList(), usedK = 0, truncated = false)
        val payload = sut.build(ctx, query = "tem promoção hoje?", lang = null)

        assertThat(payload.user).contains("CONTEXTO:")
        assertThat(payload.user).contains("<sem contexto disponível>")
        assertThat(payload.user).contains("PERGUNTA:")
    }

    @Test
    fun `should include reference index lines when citations exist`() {
        val ctx =
            BuiltContext(
                text = "[1] A\n\n[2] B",
                index =
                    listOf(
                        CitationIndex(1, "doc-1", "Title 1", 0),
                        CitationIndex(2, "doc-2", "Title 2", 3),
                    ),
                usedK = 2,
                truncated = false,
            )

        val payload = sut.build(ctx, query = "q", lang = "en")

        assertThat(payload.user).contains("REFERENCE INDEX:")
        assertThat(payload.user).contains("[1] doc-1#chunk0 — Title 1")
        assertThat(payload.user).contains("[2] doc-2#chunk3 — Title 2")
    }

    @Test
    fun `should always enforce output sections with placeholders`() {
        val ctx =
            BuiltContext(
                text = "[1] X",
                index = listOf(CitationIndex(1, "d", "t", 0)),
                usedK = 1,
                truncated = false,
            )

        val payload = sut.build(ctx, query = "q", lang = "en")

        assertThat(payload.user).contains("ANSWER:")
        assertThat(payload.user).contains("CITATIONS:")
        assertThat(payload.user).contains("<answer here>")
        assertThat(payload.user).contains("[1] <documentId>#chunk<idx>")
    }

    @Test
    fun `should omit cite and admit-unknown rules when flags are disabled`() {
        val sutNoRules =
            DefaultPromptBuilder(
                localization = localization,
                requireCitations = false,
                requireAdmitUnknown = false,
                autoDetectLang = true,
            )
        val ctx =
            BuiltContext(
                text = "[1] X",
                index = listOf(CitationIndex(1, "d", "t", 0)),
                usedK = 1,
                truncated = false,
            )

        val payload = sutNoRules.build(ctx, query = "q", lang = "en")

        assertThat(payload.system).contains("1) Answer only using information present in the CONTEXT.")
        assertThat(payload.system).contains("4) Format the output using exactly these sections: ANSWER and CITATIONS.")
        assertThat(payload.system).doesNotContain("Every factual claim must cite")
        assertThat(payload.system).doesNotContain("If the CONTEXT lacks")
    }

    @Test
    fun `should default to english when autodetect is disabled and lang is null`() {
        val sutNoAuto =
            DefaultPromptBuilder(
                localization = localization,
                requireCitations = true,
                requireAdmitUnknown = true,
                autoDetectLang = false,
            )

        val payload =
            sutNoAuto.build(
                context = BuiltContext(text = "", index = emptyList(), usedK = 0, truncated = false),
                query = "tem promoção hoje?",
                lang = null,
            )

        assertThat(payload.user).contains("CONTEXT:")
        assertThat(payload.user).doesNotContain("CONTEXTO:")
    }

    @Test
    fun `should choose english when query has no pt hints`() {
        val ctx = BuiltContext(text = "", index = emptyList(), usedK = 0, truncated = false)
        val payload = sut.build(ctx, query = "what time on sunday?", lang = null)

        assertThat(payload.user).contains("CONTEXT:")
        assertThat(payload.user).doesNotContain("CONTEXTO:")
    }

    @Test
    fun `should autodetect Portuguese by diacritics when no pt hints present`() {
        val ctx = BuiltContext(text = "", index = emptyList(), usedK = 0, truncated = false)

        val payload = sut.build(ctx, query = "pão de queijo?", lang = null)

        assertThat(payload.user).contains("CONTEXTO:")
        assertThat(payload.user).doesNotContain("CONTEXT:")
    }

    private class FakePromptLocalization : PromptLocalization {
        override fun labels(lang: String?): PromptLabels {
            val isPt = lang?.lowercase()?.startsWith("pt") == true
            return if (isPt) {
                PromptLabels(
                    systemHeader = "Você é um assistente que responde com base no CONTEXTO fornecido.",
                    ruleUseOnlyContext = "Responda apenas com informações presentes no CONTEXTO.",
                    ruleCiteAllWithN = "Toda afirmação factual deve indicar a(s) fonte(s) com [n].",
                    ruleAdmitUnknown = "Se faltar informação no CONTEXTO, diga que não há dados suficientes.",
                    ruleOutputFormat = "Formate a saída exatamente com as seções: ANSWER e CITATIONS.",
                    context = "CONTEXTO",
                    referenceIndex = "ÍNDICE DE REFERÊNCIA",
                    question = "PERGUNTA",
                    responseFormatIntro = "Formate sua resposta exatamente assim (sem adicionar outras seções):",
                    contextEmptyHint = "<sem contexto disponível>",
                    answerHere = "resposta aqui",
                )
            } else {
                PromptLabels(
                    systemHeader = "You are an assistant that must answer based on the provided CONTEXT.",
                    ruleUseOnlyContext = "Answer only using information present in the CONTEXT.",
                    ruleCiteAllWithN = "Every factual claim must cite its source(s) using [n] markers.",
                    ruleAdmitUnknown = "If the CONTEXT lacks information, state that there isn't enough data.",
                    ruleOutputFormat = "Format the output using exactly these sections: ANSWER and CITATIONS.",
                    context = "CONTEXT",
                    referenceIndex = "REFERENCE INDEX",
                    question = "QUESTION",
                    responseFormatIntro = "Format your response exactly like this (do not add extra sections):",
                    contextEmptyHint = "<no context available>",
                    answerHere = "answer here",
                )
            }
        }
    }
}
