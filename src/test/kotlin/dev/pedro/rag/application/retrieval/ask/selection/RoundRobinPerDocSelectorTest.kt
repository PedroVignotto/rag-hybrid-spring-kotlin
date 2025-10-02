package dev.pedro.rag.application.retrieval.ask.selection

import dev.pedro.rag.application.retrieval.ask.context.ContextSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoundRobinPerDocSelectorTest {
    private val sut = RoundRobinPerDocSelector()

    @Test
    fun `should respect topK and max per doc with round robin`() {
        val sources =
            listOf(
                src("A", 0),
                src("A", 1),
                src("A", 2),
                src("B", 0),
                src("B", 1),
                src("C", 0),
            )

        val selected = sut.select(sources, topK = 5, maxChunksPerDoc = 2)

        assertThat(selected.map { it.documentId to it.chunkIndex })
            .containsExactly(
                "A" to 0,
                "B" to 0,
                "C" to 0,
                "A" to 1,
                "B" to 1,
            )
    }

    @Test
    fun `should stop when no candidates are eligible`() {
        val sources = listOf(src("A", 0), src("A", 1))

        val selected = sut.select(sources, topK = 5, maxChunksPerDoc = 1)

        assertThat(selected.map { it.documentId to it.chunkIndex }).containsExactly("A" to 0)
    }

    @Test
    fun `should return empty when topK is zero or sources empty`() {
        assertThat(sut.select(emptyList(), topK = 0, maxChunksPerDoc = 2)).isEmpty()
        assertThat(sut.select(emptyList(), topK = 3, maxChunksPerDoc = 2)).isEmpty()
    }

    @Test
    fun `should preserve order of first appearance of documents`() {
        val sources =
            listOf(
                src("B", 0),
                src("B", 1),
                src("A", 0),
                src("A", 1),
                src("A", 2),
                src("C", 0),
            )

        val selected = sut.select(sources, topK = 5, maxChunksPerDoc = 2)

        assertThat(selected.map { it.documentId to it.chunkIndex })
            .containsExactly(
                "B" to 0,
                "A" to 0,
                "C" to 0,
                "B" to 1,
                "A" to 1,
            )
    }

    @Test
    fun `should respect capacity when topK smaller than number of docs`() {
        val sources =
            listOf(
                src("A", 0),
                src("B", 0),
                src("C", 0),
            )
        val selected = sut.select(sources, topK = 2, maxChunksPerDoc = 2)

        assertThat(selected.map { it.documentId to it.chunkIndex })
            .containsExactly(
                "A" to 0,
                "B" to 0,
            )
    }

    @Test
    fun `should keep per-doc chunk order as they appear in ranking`() {
        val sources =
            listOf(
                src("A", 1),
                src("A", 0),
                src("B", 0),
                src("B", 1),
            )

        val selected = sut.select(sources, topK = 3, maxChunksPerDoc = 2)

        assertThat(selected.map { it.documentId to it.chunkIndex })
            .containsExactly(
                "A" to 1,
                "B" to 0,
                "A" to 0,
            )
    }

    @Test
    fun `should cap to maxChunksPerDoc for single document even when topK is large`() {
        val sources =
            listOf(
                src("A", 0),
                src("A", 1),
                src("A", 2),
                src("A", 3),
            )

        val selected = sut.select(sources, topK = 10, maxChunksPerDoc = 2)

        assertThat(selected.map { it.documentId to it.chunkIndex })
            .containsExactly("A" to 0, "A" to 1)
    }

    @Test
    fun `should skip docs without next chunk in later rounds`() {
        val sources =
            listOf(
                src("A", 0),
                src("B", 0),
                src("B", 1),
            )

        val selected = sut.select(sources, topK = 3, maxChunksPerDoc = 2)

        assertThat(selected.map { it.documentId to it.chunkIndex })
            .containsExactly(
                "A" to 0,
                "B" to 0,
                "B" to 1,
            )
    }

    @Test
    fun `should validate inputs`() {
        assertThrows<IllegalArgumentException> {
            sut.select(listOf(src("A", 0)), topK = -1, maxChunksPerDoc = 2)
        }
        assertThrows<IllegalArgumentException> {
            sut.select(listOf(src("A", 0)), topK = 3, maxChunksPerDoc = 0)
        }
    }

    private fun src(
        doc: String,
        idx: Int,
    ) = ContextSource(
        documentId = doc,
        title = "t-$doc",
        chunkIndex = idx,
        text = "x",
        score = 1.0,
    )
}
