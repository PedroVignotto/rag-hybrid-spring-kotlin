package dev.pedro.rag.api.retrieval.mappers

import dev.pedro.rag.api.retrieval.request.IngestRequest
import dev.pedro.rag.api.retrieval.response.IngestResponse
import dev.pedro.rag.api.retrieval.response.SearchMatchResponse
import dev.pedro.rag.api.retrieval.response.SearchResponse
import dev.pedro.rag.application.retrieval.ingest.dto.IngestInput
import dev.pedro.rag.application.retrieval.usecase.ingest.IngestOutput
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch

fun IngestRequest.toInput(): IngestInput =
    IngestInput(
        documentId = DocumentId(documentId),
        text = text,
        baseMetadata = metadata ?: emptyMap(),
        chunkSize = chunkSize,
        overlap = overlap,
    )

fun IngestOutput.toResponse(): IngestResponse =
    IngestResponse(
        documentId = documentId.value,
        chunksIngested = chunksIngested,
    )

fun List<SearchMatch>.toResponse(): SearchResponse = SearchResponse(matches = map { it.toResponse() })

fun SearchMatch.toResponse(): SearchMatchResponse =
    SearchMatchResponse(
        documentId = documentId.value,
        text = chunk.text,
        score = score,
        metadata = chunk.metadata,
    )
