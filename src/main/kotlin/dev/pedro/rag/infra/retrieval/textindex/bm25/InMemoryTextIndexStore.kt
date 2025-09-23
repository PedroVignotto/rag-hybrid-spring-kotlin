package dev.pedro.rag.infra.retrieval.textindex.bm25

import dev.pedro.rag.application.retrieval.ports.TextIndexPort
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.max

class InMemoryTextIndexStore(
    private val stopWordsEnabled: Boolean = false,
    private val stopWords: Set<String> = emptySet(),
    private val bm25TermFrequencySaturation: Double = 1.2,
    private val bm25LengthNormalization: Double = 0.75,
) : TextIndexPort {
    private companion object {
        const val KEY_CHUNK_INDEX = "chunk_index"
    }

    private data class DocumentKey(val documentId: DocumentId, val chunkIndex: Int)

    private val postings = linkedMapOf<String, MutableMap<DocumentKey, Int>>()
    private val docLengths = linkedMapOf<DocumentKey, Int>()
    private val docChunks = linkedMapOf<DocumentKey, TextChunk>()
    private val docIdToKeys = linkedMapOf<DocumentId, MutableSet<DocumentKey>>()

    @Synchronized
    override fun index(
        documentId: DocumentId,
        chunks: List<TextChunk>,
    ): Int {
        if (chunks.isEmpty()) return 0
        var count = 0
        chunks.forEachIndexed { idx, chunk ->
            val key =
                DocumentKey(
                    documentId = documentId,
                    chunkIndex = chunk.metadata[KEY_CHUNK_INDEX]?.toIntOrNull() ?: idx,
                )
            removeDocKey(key)
            val tokens = tokenize(chunk.text)
            docChunks[key] = chunk
            docLengths[key] = tokens.size
            docIdToKeys.getOrPut(documentId) { linkedSetOf() }.add(key)
            if (tokens.isNotEmpty()) {
                val tfCounts = tokens.groupingBy { it }.eachCount()
                tfCounts.forEach { (term, tf) ->
                    postings.getOrPut(term) { linkedMapOf() }[key] = tf
                }
            }
            count++
        }
        return count
    }

    @Synchronized
    override fun delete(documentId: DocumentId): Int {
        val keys = docIdToKeys.remove(documentId) ?: return 0
        var removed = 0
        keys.forEach { key ->
            removeDocKey(key)
            removed++
        }
        return removed
    }

    @Synchronized
    override fun search(
        query: String,
        width: Int,
        filter: Map<String, String>?,
    ): List<SearchMatch> {
        if (query.isBlank() || width <= 0 || docLengths.isEmpty()) return emptyList()
        val terms = tokenize(query).distinct()
        if (terms.isEmpty()) return emptyList()
        val allowedKeys: Set<DocumentKey>? =
            filter?.takeIf { it.isNotEmpty() }?.let { f ->
                docChunks
                    .filter { (_, chunk) -> f.all { (k, v) -> chunk.metadata[k] == v } }
                    .keys
                    .toSet()
                    .also { if (it.isEmpty()) return emptyList() }
            }
        val nDocs = docLengths.size.toDouble()
        val avgDl = docLengths.values.average().takeIf { !it.isNaN() } ?: 0.0
        val scores = mutableMapOf<DocumentKey, Double>()
        terms.forEach { term ->
            val posting = postings[term] ?: return@forEach
            val df = posting.size.toDouble()
            val idf = bm25Idf(nDocs, df)
            posting.forEach { (key, tf) ->
                if (allowedKeys != null && key !in allowedKeys) return@forEach
                val dl = docLengths[key] ?: return@forEach
                val s = idf * bm25Tf(tf, dl, avgDl)
                scores[key] = (scores[key] ?: 0.0) + s
            }
        }
        if (scores.isEmpty()) return emptyList()
        return scores.entries
            .sortedWith(
                compareByDescending<Map.Entry<DocumentKey, Double>> { it.value }
                    .thenBy { it.key.documentId.toString() }
                    .thenBy { it.key.chunkIndex },
            )
            .take(width)
            .mapNotNull { (key, score) ->
                val chunk = docChunks[key] ?: return@mapNotNull null
                SearchMatch(documentId = key.documentId, chunk = chunk, score = score)
            }
    }

    @Synchronized
    override fun size(): Int = docLengths.size

    private fun removeDocKey(key: DocumentKey) {
        postings.values.forEach { it.remove(key) }
        val emptyTerms = postings.filterValues { it.isEmpty() }.keys.toList()
        emptyTerms.forEach { postings.remove(it) }
        docLengths.remove(key)
        docChunks.remove(key)
        docIdToKeys[key.documentId]?.remove(key)
        if (docIdToKeys[key.documentId]?.isEmpty() == true) {
            docIdToKeys.remove(key.documentId)
        }
    }

    private fun bm25Idf(
        nDocs: Double,
        df: Double,
    ): Double {
        return ln(((nDocs - df + 0.5) / (df + 0.5)) + 1.0)
    }

    private fun bm25Tf(
        tf: Int,
        dl: Int,
        avgDl: Double,
    ): Double {
        if (dl == 0) return 0.0
        val tfD = tf.toDouble()
        val dlAdj = dl.toDouble()
        val denominator =
            tfD + bm25TermFrequencySaturation * (
                1.0 - bm25LengthNormalization + bm25LengthNormalization * (dlAdj / max(1.0, avgDl))
            )
        return (tfD * (bm25TermFrequencySaturation + 1.0)) / denominator
    }

    private fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val noAccentsLower =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
        val cleaned = noAccentsLower.replace("[^\\p{L}\\p{Nd}]".toRegex(), " ")
        val raw = cleaned.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        return if (stopWordsEnabled && stopWords.isNotEmpty()) {
            raw.filterNot { it in stopWords }
        } else {
            raw
        }
    }
}
