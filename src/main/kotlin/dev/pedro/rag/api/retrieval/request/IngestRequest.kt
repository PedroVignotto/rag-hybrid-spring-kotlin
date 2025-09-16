package dev.pedro.rag.api.retrieval.request

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class IngestRequest(
    @field:NotBlank(message = "documentId must not be blank")
    val documentId: String,
    @field:NotBlank(message = "text must not be blank")
    val text: String,
    val metadata: Map<String, String>? = emptyMap(),
    @field:Min(1, message = "chunkSize must be >= 1")
    val chunkSize: Int,
    @field:Min(0, message = "overlap must be >= 0")
    val overlap: Int,
)
