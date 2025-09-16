package dev.pedro.rag.api.retrieval

import dev.pedro.rag.api.retrieval.mappers.toInput
import dev.pedro.rag.api.retrieval.mappers.toResponse
import dev.pedro.rag.api.retrieval.request.IngestRequest
import dev.pedro.rag.api.retrieval.request.SearchRequest
import dev.pedro.rag.api.retrieval.response.IngestResponse
import dev.pedro.rag.api.retrieval.response.SearchResponse
import dev.pedro.rag.application.retrieval.ingest.usecase.IngestUseCase
import dev.pedro.rag.application.retrieval.usecase.SearchUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Retrieval")
@Validated
@RestController
@RequestMapping("/v1/retrieval", produces = [MediaType.APPLICATION_JSON_VALUE])
class RetrievalController(
    private val ingestUseCase: IngestUseCase,
    private val searchUseCase: SearchUseCase,
) {
    @PostMapping("/ingest", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Ingest a document into the vector store",
        description = "Split text into overlapping chunks, embed them, and upsert into a collection namespaced by {provider, model, dim}.",
    )
    fun ingest(
        @RequestBody @Valid request: IngestRequest,
    ): IngestResponse = ingestUseCase.ingest(request.toInput()).toResponse()

    @PostMapping("/search", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Search top-K chunks by semantic similarity",
        description = "Embed the query and perform similarity search (cosine/dot depending on normalization).",
    )
    fun search(
        @RequestBody @Valid request: SearchRequest,
    ): SearchResponse = searchUseCase.search(queryText = request.query, topK = request.topK, filter = request.filter).toResponse()
}
