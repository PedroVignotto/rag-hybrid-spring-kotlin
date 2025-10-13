package dev.pedro.rag.api.retrieval

import dev.pedro.rag.api.retrieval.mappers.toInput
import dev.pedro.rag.api.retrieval.mappers.toResponse
import dev.pedro.rag.api.retrieval.request.AskRequest
import dev.pedro.rag.api.retrieval.response.AskResponse
import dev.pedro.rag.application.retrieval.ask.usecase.AskUseCase
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping(
    path = ["/v1/retrieval/ask"],
    produces = [MediaType.APPLICATION_JSON_VALUE],
)
class AskController(
    private val askUseCase: AskUseCase,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "RAG ask (hybrid search + LLM with citations)",
        description = """
            Runs hybrid search (vector + BM25), builds a context (topK with per-doc cap),
            asks the LLM, and returns an answer with citations.
            Optional 'filter' narrows by chunk metadata (e.g. {"store":"hq"}).
            'usedK' is the number of chunks used; 'notes' may be "no-matches", "llm-no-citations", or "extractive-fallback".
        """
    )
    fun ask(
        @Valid @RequestBody request: AskRequest,
    ): AskResponse = askUseCase.handle(request.toInput()).toResponse()
}
