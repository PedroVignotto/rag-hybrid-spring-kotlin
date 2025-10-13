package dev.pedro.rag.api.retrieval

import dev.pedro.rag.api.retrieval.mappers.toInput
import dev.pedro.rag.api.retrieval.mappers.toResponse
import dev.pedro.rag.api.retrieval.request.AskRequest
import dev.pedro.rag.api.retrieval.response.AskResponse
import dev.pedro.rag.application.retrieval.ask.usecase.AskUseCase
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
    fun ask(
        @Valid @RequestBody request: AskRequest,
    ): AskResponse = askUseCase.handle(request.toInput()).toResponse()
}
