package dev.pedro.rag.application.retrieval.ports

import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

abstract class VectorStoreContractTest {
    protected abstract fun sut(): VectorStorePort

    protected abstract fun collection(): CollectionSpec

    @Test
    fun `search should return empty when collection has no entries`() {
        val results =
            sut().search(
                collection = collection(),
                query = vec(floatArrayOf(1f, 0f, 0f), normalized = true),
                topK = 3,
                filter = null,
            )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `upsert then search should order by score desc and clamp score`() {
        val sut = sut()
        val col = collection()
        sut.upsert(
            col,
            DocumentId("doc1"),
            listOf(
                chunk("A1") to vec(floatArrayOf(0.9f, 0.1f, 0f), true),
                chunk("A2") to vec(floatArrayOf(0.7f, 0.2f, 0.1f), true),
            ),
        )
        sut.upsert(
            col,
            DocumentId("doc2"),
            listOf(
                chunk("B1", mapOf("foo" to "bar")) to vec(floatArrayOf(0.1f, 0.9f, 0f), true),
            ),
        )

        val results: List<SearchMatch> =
            sut.search(
                collection = col,
                query = vec(floatArrayOf(1f, 0f, 0f), true),
                topK = 3,
                filter = null,
            )

        assertEquals(3, results.size)
        assertEquals("doc1", results[0].documentId.value)
        assertTrue(results[0].score >= results[1].score)
        assertTrue(results.all { it.score in 0.0..1.0 })
    }

    @Test
    fun `filter should keep only exact metadata matches (AND)`() {
        val sut = sut()
        val col = collection()
        sut.upsert(
            col,
            DocumentId("doc3"),
            listOf(
                chunk("C1", mapOf("location" to "hq", "type" to "menu")) to vec(floatArrayOf(1f, 0f, 0f), true),
                chunk("C2", mapOf("location" to "branch", "type" to "menu")) to vec(floatArrayOf(1f, 0f, 0f), true),
            ),
        )

        val filtered =
            sut.search(
                collection = col,
                query = vec(floatArrayOf(1f, 0f, 0f), true),
                topK = 5,
                filter = mapOf("location" to "hq", "type" to "menu"),
            )

        assertEquals(1, filtered.size)
        assertEquals("C1", filtered.first().chunk.text)
    }

    @Test
    fun `cosine path should work when vectors are not normalized`() {
        val sut = sut()
        val col = collection()
        sut.upsert(
            col,
            DocumentId("doc5"),
            listOf(
                chunk("E1") to vec(floatArrayOf(2f, 0f, 0f), false),
                chunk("E2") to vec(floatArrayOf(1f, 1f, 0f), false),
            ),
        )

        val results = sut.search(col, vec(floatArrayOf(1f, 0f, 0f), false), topK = 2, filter = null)

        assertEquals(listOf("E1", "E2"), results.map { it.chunk.text })
    }

    @Test
    fun `topK less than or equal to zero should return one result`() {
        val sut = sut()
        val col = collection()
        sut.upsert(
            col,
            DocumentId("doc6"),
            listOf(
                chunk("F1") to vec(floatArrayOf(1f, 0f, 0f), true),
                chunk("F2") to vec(floatArrayOf(0.99f, 0.01f, 0f), true),
            ),
        )

        val outZero = sut.search(col, vec(floatArrayOf(1f, 0f, 0f), true), topK = 0, filter = null)
        val outNegative = sut.search(col, vec(floatArrayOf(1f, 0f, 0f), true), topK = -10, filter = null)

        assertEquals(1, outZero.size)
        assertEquals(1, outNegative.size)
    }

    @Test
    fun `should use stable ordering when scores are equal (documentId then chunk_index)`() {
        val sut = sut()
        val col = collection()
        sut.upsert(
            col,
            DocumentId("docSame"),
            listOf(
                chunk("G0", metadata = mapOf("chunk_index" to "0")) to vec(floatArrayOf(1f, 0f, 0f), true),
                chunk("G1", metadata = mapOf("chunk_index" to "1")) to vec(floatArrayOf(1f, 0f, 0f), true),
            ),
        )

        val out = sut.search(col, vec(floatArrayOf(1f, 0f, 0f), true), topK = 2, filter = null)

        assertEquals(listOf("G0", "G1"), out.map { it.chunk.text })
    }

    @Test
    fun `null filter and empty filter should behave the same`() {
        val sut = sut()
        val col = collection()
        sut.upsert(
            col,
            DocumentId("doc7"),
            listOf(
                chunk("H1", mapOf("location" to "hq")) to vec(floatArrayOf(1f, 0f, 0f), true),
                chunk("H2", mapOf("location" to "branch")) to vec(floatArrayOf(0.9f, 0.1f, 0f), true),
            ),
        )

        val outNull = sut.search(col, vec(floatArrayOf(1f, 0f, 0f), true), topK = 5, filter = null)
        val outEmpty = sut.search(col, vec(floatArrayOf(1f, 0f, 0f), true), topK = 5, filter = emptyMap())

        assertEquals(outNull.map { it.chunk.text }, outEmpty.map { it.chunk.text })
    }

    @Test
    fun `deleteByDocumentId should remove all entries and return the deleted count`() {
        val sut = sut()
        val col = collection()
        sut.upsert(
            col,
            DocumentId("doc4"),
            listOf(
                chunk("D1") to vec(floatArrayOf(0f, 1f, 0f), true),
                chunk("D2") to vec(floatArrayOf(0f, 1f, 0f), true),
            ),
        )
        val before = sut.count(col)

        val deleted = sut.deleteByDocumentId(col, DocumentId("doc4"))
        val results = sut.search(col, vec(floatArrayOf(0f, 1f, 0f), true), topK = 3, filter = null)
        val after = sut.count(col)

        assertTrue(before >= 2L)
        assertEquals(2, deleted)
        assertTrue(results.isEmpty())
        assertEquals(0L, after)
    }

    @Test
    fun `deleteByDocumentId should be a no-op for absent documentId and return 0`() {
        val sut = sut()
        val col = collection()
        sut.upsert(
            col,
            DocumentId("doc8"),
            listOf(chunk("I1") to vec(floatArrayOf(0f, 1f, 0f), true)),
        )
        val before = sut.count(col)

        val deleted = sut.deleteByDocumentId(col, DocumentId("does-not-exist"))
        val after = sut.count(col)

        assertEquals(0, deleted)
        assertEquals(before, after)
    }

    @Test
    fun `deleteByDocumentId should be idempotent for absent documentId`() {
        val sut = sut()
        val col = collection()
        val before = sut.count(col)

        val first = sut.deleteByDocumentId(col, DocumentId("nope"))
        val second = sut.deleteByDocumentId(col, DocumentId("nope"))
        val after = sut.count(col)

        assertEquals(0, first)
        assertEquals(0, second)
        assertEquals(before, after)
    }

    protected fun chunk(
        text: String,
        metadata: Map<String, String> = emptyMap(),
    ) = TextChunk(text = text, metadata = metadata)

    protected fun vec(
        values: FloatArray,
        normalized: Boolean,
    ) = EmbeddingVector(values = values, dim = values.size, normalized = normalized)
}
