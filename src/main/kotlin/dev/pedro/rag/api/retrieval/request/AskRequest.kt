package dev.pedro.rag.api.retrieval.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class AskRequest(
    @field:NotBlank(message = "query must not be blank")
    val query: String,
    @field:Min(1)
    @field:Max(100)
    val topK: Int = 10,
    val filter: Map<String, String> = emptyMap(),
    val lang: String? = null,
)
