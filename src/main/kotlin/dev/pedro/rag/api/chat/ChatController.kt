package dev.pedro.rag.api.chat

import dev.pedro.rag.api.chat.mappers.toApi
import dev.pedro.rag.api.chat.mappers.toDomain
import dev.pedro.rag.api.chat.request.ApiChatRequest
import dev.pedro.rag.api.chat.response.ApiChatResponse
import dev.pedro.rag.api.chat.support.ChatSseBridge
import dev.pedro.rag.application.chat.ChatUseCase
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping(value = ["/v1/chat"], produces = [MediaType.APPLICATION_JSON_VALUE])
class ChatController(private val useCase: ChatUseCase, private val sseBridge: ChatSseBridge) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun post(
        @Valid @RequestBody req: ApiChatRequest,
    ): ApiChatResponse = useCase.handle(req.toDomain()).toApi()

    @PostMapping(
        path = ["/stream"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    fun stream(@Valid @RequestBody body: ApiChatRequest): SseEmitter {
        val input = body.toDomain()
        val emitter = SseEmitter(0L)
        sseBridge.stream(input, emitter)
        return emitter
    }
}
