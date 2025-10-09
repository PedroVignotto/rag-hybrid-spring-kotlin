package dev.pedro.rag.application.i18n

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class LocaleUtilsTest {

    @Test
    fun `should resolve pt-BR for common variants`() {
        val a = resolvePtBrOrFallback("pt")
        val b = resolvePtBrOrFallback("pt-BR")
        val c = resolvePtBrOrFallback("pt_br")
        val d = resolvePtBrOrFallback("PT-BR")
        val e = resolvePtBrOrFallback("  pt-br  ")

        listOf(a, b, c, d, e).forEach { loc ->
            assertThat(loc.language).isEqualTo("pt")
            assertThat(loc.country).isEqualTo("BR")
        }
    }

    @Test
    fun `should fallback to EN when lang is null empty or unknown`() {
        assertThat(resolvePtBrOrFallback(null)).isEqualTo(Locale.ENGLISH)
        assertThat(resolvePtBrOrFallback("")).isEqualTo(Locale.ENGLISH)
        assertThat(resolvePtBrOrFallback("fr")).isEqualTo(Locale.ENGLISH)
        assertThat(resolvePtBrOrFallback("en")).isEqualTo(Locale.ENGLISH)
    }

    @Test
    fun `should normalize underscores and whitespace`() {
        val loc = resolvePtBrOrFallback("  pt_br ")
        assertThat(loc.language).isEqualTo("pt")
        assertThat(loc.country).isEqualTo("BR")
    }
}