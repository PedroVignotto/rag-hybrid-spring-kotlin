package dev.pedro.rag.api.chat.support

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.api.chat.mappers.toApiStreamResponse
import dev.pedro.rag.api.chat.response.stream.ApiChatDeltaResponse
import dev.pedro.rag.api.chat.response.stream.ApiChatErrorResponse
import dev.pedro.rag.application.chat.ChatUseCase
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executor

class ChatSseBridge(
    private val useCase: ChatUseCase,
    private val mapper: ObjectMapper,
    private val executor: Executor? = null
) {
    companion object {
        private const val EV_DELTA = "delta"
        private const val EV_USAGE = "usage"
        private const val EV_DONE  = "done"
        private const val EV_ERROR = "error"
    }

    fun stream(input: ChatInput, emitter: SseEmitter) {
        emitter.onTimeout { safeComplete(emitter) }
        runTask {
            try {
                useCase.handleStream(
                    input = input,
                    onDelta = { token -> sendJson(emitter, EV_DELTA, ApiChatDeltaResponse(token)) },
                    onUsage = { usage -> sendJson(emitter, EV_USAGE, usage.toApiStreamResponse()) }
                )
                sendJson(emitter, EV_DONE, emptyMap<String, String>())
            } catch (e: OllamaHttpException) {
                sendJson(emitter, EV_ERROR, ApiChatErrorResponse(e.status, upstreamBody = e.responseBody))
            } catch (t: Throwable) {
                sendJson(emitter, EV_ERROR, ApiChatErrorResponse(500, message = t.message ?: "Internal error"))
            } finally {
                safeComplete(emitter)
            }
        }
    }

    private fun sendJson(emitter: SseEmitter, event: String, payload: Any) {
        try {
            val json = mapper.writeValueAsString(payload)
            emitter.send(SseEmitter.event().name(event).data(json, MediaType.APPLICATION_JSON))
        } catch (_: Exception) {
            safeComplete(emitter)
        }
    }

    private fun safeComplete(emitter: SseEmitter) { try { emitter.complete() } catch (_: Exception) {} }

    private fun runTask(task: Runnable) { if (executor != null) executor.execute(task) else task.run() }
}