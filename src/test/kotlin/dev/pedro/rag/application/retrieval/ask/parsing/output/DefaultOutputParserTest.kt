package dev.pedro.rag.application.retrieval.ask.parsing.output

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class DefaultOutputParserTest {

    private val sut = DefaultOutputParser()

    @Test
    fun `should parse answer and citation numbers (happy path)`() {
        val raw = """
            ANSWER:
            Veggie available and Pix accepted. [1][2]
            
            CITATIONS:
            [1] ze-menu-001#chunk0
            [2] ze-promo-001#chunk3
        """.trimIndent()

        val result = sut.parse(raw)

        Assertions.assertThat(result.answer).isEqualTo("Veggie available and Pix accepted. [1][2]")
        Assertions.assertThat(result.citationNs).containsExactly(1, 2)
    }

    @Test
    fun `should handle empty citations block (no markers)`() {
        val raw = """
            ANSWER:
            Only one chunk fits the budget.
            
            CITATIONS:
        """.trimIndent()

        val result = sut.parse(raw)

        Assertions.assertThat(result.answer).isEqualTo("Only one chunk fits the budget.")
        Assertions.assertThat(result.citationNs).isEmpty()
    }

    @Test
    fun `should deduplicate and preserve first-appearance order of markers`() {
        val raw = """
            ANSWER:
            Mixed markers. [3][1][3][2]
            
            CITATIONS:
            [3] c#chunk2
            [1] a#chunk0
            [3] c#chunk2
            [2] b#chunk1
        """.trimIndent()

        val result = sut.parse(raw)

        Assertions.assertThat(result.citationNs).containsExactly(3, 1, 2)
    }

    @Test
    fun `should be case-insensitive on section headers`() {
        val raw = """
            answer:
            Lowercase headers still work. [1]
            
            citations:
            [1] doc#chunk0
        """.trimIndent()

        val result = sut.parse(raw)

        Assertions.assertThat(result.answer).isEqualTo("Lowercase headers still work. [1]")
        Assertions.assertThat(result.citationNs).containsExactly(1)
    }

    @Test
    fun `should extract markers from answer when enabled and no citations section`() {
        val sutWithFallback = DefaultOutputParser(allowNsFromAnswer = true)
        val raw = """
            ANSWER:
            Model only cited inside the answer [1] without a citations block.
        """.trimIndent()

        val result = sutWithFallback.parse(raw)

        Assertions.assertThat(result.answer).contains("Model only cited inside the answer [1]")
        Assertions.assertThat(result.citationNs).containsExactly(1)
    }

    @Test
    fun `should fallback to raw when no sections are present`() {
        val raw = "Just text without sections [1]."

        val result = sut.parse(raw)

        Assertions.assertThat(result.answer).isEqualTo("Just text without sections [1].")
        Assertions.assertThat(result.citationNs).isEmpty()
    }

    @Test
    fun `should NOT extract markers from answer when allowNsFromAnswer is false`() {
        val raw = """
            ANSWER:
            Only inside the answer [2].
        """.trimIndent()

        val result = sut.parse(raw)

        Assertions.assertThat(result.answer).contains("Only inside the answer [2].")
        Assertions.assertThat(result.citationNs).isEmpty()
    }

    @Test
    fun `should ignore malformed markers in citations block`() {
        val raw = """
            ANSWER:
            Mixed content.
    
            CITATIONS:
            [x] doc#chunk0
            [3a] doc#chunk1
            [ 4 ] doc#chunk2
            [] doc#chunk3
            [2] doc#chunk4
        """.trimIndent()

        val result = sut.parse(raw)

        Assertions.assertThat(result.citationNs).containsExactly(2)
    }
}