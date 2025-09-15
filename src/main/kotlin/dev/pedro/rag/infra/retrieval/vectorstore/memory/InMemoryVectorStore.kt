package dev.pedro.rag.infra.retrieval.vectorstore.memory

import dev.pedro.rag.application.retrieval.ports.VectorStorePort
import dev.pedro.rag.domain.retrieval.CollectionSpec
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.EmbeddingVector
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import dev.pedro.rag.infra.retrieval.vectorstore.support.VectorSearchSupport.cosineSimilarity
import dev.pedro.rag.infra.retrieval.vectorstore.support.VectorSearchSupport.matchesMetadata
import dev.pedro.rag.infra.retrieval.vectorstore.support.VectorSearchSupport.sortByScoreAndStability
import java.util.concurrent.ConcurrentHashMap

class InMemoryVectorStore : VectorStorePort {
    private val indices: ConcurrentHashMap<CollectionSpec, MutableList<IndexedItem>> = ConcurrentHashMap()

    override fun upsert(
        collection: CollectionSpec,
        documentId: DocumentId,
        items: List<Pair<TextChunk, EmbeddingVector>>,
    ) {
        val entries = indices.computeIfAbsent(collection) { mutableListOf() }
        synchronized(entries) {
            entries.removeAll { it.documentId == documentId }
            items.forEach { (chunk, vector) ->
                entries.add(IndexedItem(documentId = documentId, chunk = chunk, vector = vector))
            }
        }
    }

    override fun search(
        collection: CollectionSpec,
        query: EmbeddingVector,
        topK: Int,
        filter: Map<String, String>?,
    ): List<SearchMatch> {
        val entries = indices[collection] ?: return emptyList()
        val entriesSnapshot = snapshot(entries)
        if (entriesSnapshot.isEmpty()) return emptyList()
        val candidates = applyMetadataFilter(entriesSnapshot, filter)
        if (candidates.isEmpty()) return emptyList()
        val scoredMatches = scoreCandidates(candidates, query)
        val effectiveTopK = topK.coerceAtLeast(1)
        return sortByScoreAndStability(scoredMatches)
            .take(effectiveTopK.coerceAtMost(scoredMatches.size))
    }

    override fun deleteByDocumentId(
        collection: CollectionSpec,
        documentId: DocumentId,
    ) {
        val entries = indices[collection] ?: return
        synchronized(entries) {
            entries.removeAll { it.documentId == documentId }
        }
    }

    override fun count(collection: CollectionSpec): Long {
        val entries = indices[collection] ?: return 0L
        return synchronized(entries) { entries.size.toLong() }
    }

    private fun snapshot(entries: MutableList<IndexedItem>): List<IndexedItem> = synchronized(entries) { entries.toList() }

    private fun applyMetadataFilter(
        items: List<IndexedItem>,
        filter: Map<String, String>?,
    ): List<IndexedItem> = items.filter { matchesMetadata(it.chunk.metadata, filter) }

    private fun scoreCandidates(
        items: List<IndexedItem>,
        query: EmbeddingVector,
    ): List<SearchMatch> =
        items.map { item ->
            val score = cosineSimilarity(query, item.vector).coerceIn(0.0, 1.0)
            SearchMatch(documentId = item.documentId, chunk = item.chunk, score = score)
        }
}

private class IndexedItem(
    val documentId: DocumentId,
    val chunk: TextChunk,
    val vector: EmbeddingVector,
)
