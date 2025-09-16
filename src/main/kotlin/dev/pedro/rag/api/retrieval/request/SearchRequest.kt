package dev.pedro.rag.api.retrieval.request

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class SearchRequest(
    @field:NotBlank(message = "query must not be blank")
    val query: String,
    @field:Min(1, message = "topK must be >= 1")
    val topK: Int,
    val filter: Map<String, String>? = null,
)
