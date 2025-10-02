package dev.pedro.rag.application.retrieval.ask.context

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultContextBuilderTest {
    private val sut = DefaultContextBuilder()

    @Test
    fun `should respect budget and set truncated when exceeding`() {
        val sources =
            listOf(
                src("A", 0, "x".repeat(1200), 0.9),
                src("B", 0, "y".repeat(1200), 0.8),
                src("C", 0, "z".repeat(1200), 0.7),
            )

        val result = sut.build(sources, budgetChars = 2000)

        assertThat(result.text.length).isLessThanOrEqualTo(2000)
        assertThat(result.truncated).isTrue()
        assertThat(result.usedK).isGreaterThanOrEqualTo(1)
        assertThat(result.index).hasSize(result.usedK)
        assertThat(result.text).contains("[1] ")
    }

    @Test
    fun `should number chunks and build index in order`() {
        val sources =
            listOf(
                src("A", 0, "one", 0.9),
                src("B", 0, "two", 0.8),
                src("A", 1, "three", 0.7),
            )

        val result = sut.build(sources, budgetChars = 10_000)

        assertThat(result.index.map { it.n }).containsExactly(1, 2, 3)
        assertThat(result.index.map { it.documentId }).containsExactly("A", "B", "A")
        assertThat(result.text).contains("[1] one")
        assertThat(result.text).contains("[2] two")
        assertThat(result.text).contains("[3] three")
        assertThat(result.truncated).isFalse()
    }

    @Test
    fun `should insert blank line between chunks`() {
        val sources =
            listOf(
                src("A", 0, "first", 0.9),
                src("B", 0, "second", 0.8),
            )

        val result = sut.build(sources, budgetChars = 10_000)

        assertThat(result.text).contains("\n\n[2] ")
    }

    @Test
    fun `should sanitize whitespace`() {
        val sources =
            listOf(
                src("A", 0, "alpha   \n  beta\t\tgamma", 0.9),
                src("B", 0, "delta", 0.8),
            )

        val result = sut.build(sources, budgetChars = 10_000)

        assertThat(result.text).contains("[1] alpha beta gamma")
        assertThat(result.text).doesNotContain("  ")
        assertThat(result.truncated).isFalse()
    }

    @Test
    fun `should return empty when no sources`() {
        val result = sut.build(emptyList(), budgetChars = 100)

        assertThat(result.text).isEmpty()
        assertThat(result.usedK).isEqualTo(0)
        assertThat(result.truncated).isFalse()
    }

    @Test
    fun `should mark truncated when only prefix fits (no space for text)`() {
        val lenFirst = 91
        val budget = lenFirst + 4 + 5
        val sources =
            listOf(
                src("A", 0, "a".repeat(lenFirst), 1.0),
                src("B", 0, "b".repeat(50), 0.9),
            )

        val result = sut.build(sources, budget)

        assertThat(result.usedK).isEqualTo(1)
        assertThat(result.truncated).isTrue()
        assertThat(result.text).doesNotContain("[2]")
    }

    @Test
    fun `should not append ellipsis when truncated text exactly fills remaining`() {
        val lenFirst = 89
        val budget = lenFirst + 11
        val sources =
            listOf(
                src("A", 0, "a".repeat(lenFirst), 1.0),
                src("B", 0, "b".repeat(100), 0.9),
            )

        val result = sut.build(sources, budget)

        assertThat(result.usedK).isEqualTo(2)
        assertThat(result.truncated).isTrue()
        assertThat(result.text).contains("[2] b")
        assertThat(result.text).doesNotContain(" [...]")
    }

    @Test
    fun `should skip whitespace-only chunks and keep numbering`() {
        val sources =
            listOf(
                src("A", 0, "   \n\t  ", 1.0),
                src("B", 0, "hello", 0.9),
            )

        val result = sut.build(sources, budgetChars = 100)

        assertThat(result.usedK).isEqualTo(1)
        assertThat(result.text).startsWith("[1] hello")
        assertThat(result.index.map { it.n }).containsExactly(1)
        assertThat(result.truncated).isFalse()
    }

    @Test
    fun `should mark truncated when there is no remaining budget before next chunk`() {
        val lenFirst = 96
        val budget = lenFirst + 4
        val sources =
            listOf(
                src("A", 0, "a".repeat(lenFirst), 1.0),
                src("B", 0, "bbb", 0.9),
            )

        val result = sut.build(sources, budget)

        assertThat(result.usedK).isEqualTo(1)
        assertThat(result.truncated).isTrue()
        assertThat(result.text).doesNotContain("[2]")
    }

    @Test
    fun `should validate inputs`() {
        assertThrows<IllegalArgumentException> { sut.build(emptyList(), budgetChars = 0) }
        assertThrows<IllegalArgumentException> { sut.build(emptyList(), budgetChars = -5) }
    }

    private fun src(
        doc: String,
        idx: Int,
        text: String,
        score: Double,
    ) = ContextSource(
        documentId = doc,
        title = "t-$doc",
        chunkIndex = idx,
        text = text,
        score = score,
    )
}
