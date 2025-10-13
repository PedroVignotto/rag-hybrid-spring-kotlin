package dev.pedro.rag.api.retrieval.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AskResponse(
    val answer: String,
    val citations: List<Citation>,
    val usedK: Int,
    val notes: String? = null,
) {
    data class Citation(
        val documentId: String,
        val title: String,
        val chunkIndex: Int,
    )
}
