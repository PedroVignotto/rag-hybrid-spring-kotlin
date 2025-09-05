package dev.pedro.rag.api.chat

import dev.pedro.rag.api.chat.mappers.toApi
import dev.pedro.rag.api.chat.mappers.toDomain
import dev.pedro.rag.api.chat.request.ApiChatRequest
import dev.pedro.rag.api.chat.response.ApiChatResponse
import dev.pedro.rag.application.chat.ChatUseCase
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(value = ["/v1/chat"], produces = [MediaType.APPLICATION_JSON_VALUE])
class ChatController(private val useCase: ChatUseCase) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun post(@Valid @RequestBody req: ApiChatRequest): ApiChatResponse =
        useCase.handle(req.toDomain()).toApi()
}