package dev.pedro.rag.infra.llm.ollama.chat.client

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.config.llm.LlmProperties
import dev.pedro.rag.infra.llm.ollama.chat.request.OllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.chat.response.OllamaChatResponse
import dev.pedro.rag.infra.llm.ollama.chat.response.OllamaChatStreamChunkResponse
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.support.NdjsonStreamProcessor
import dev.pedro.rag.infra.llm.ollama.support.OllamaHttpSupport
import java.net.URI
import java.net.http.HttpClient

class OllamaChatHttpClient(
    private val http: HttpClient,
    private val mapper: ObjectMapper,
    private val properties: LlmProperties.Ollama,
    private val streamProcessor: NdjsonStreamProcessor,
) {
    private val endpoint: URI =
        OllamaHttpSupport.endpoint(properties.baseUrl, "/api/chat")

    fun chat(payload: OllamaChatRequest): OllamaChatResponse {
        val jsonBody = serializeEffectiveRequestJson(payload, forceStream = false)
        val request = OllamaHttpSupport.buildJsonPost(endpoint, properties.requestTimeout, jsonBody)
        val response = OllamaHttpSupport.sendForStringAndEnsureSuccess(http, request)
        return parseAndValidateChatResponseString(response.body())
    }

    fun chatStream(
        payload: OllamaChatRequest,
        onDelta: (String) -> Unit,
        onDoneChunk: ((OllamaChatStreamChunkResponse) -> Unit)? = null,
    ) {
        val jsonBody = serializeEffectiveRequestJson(payload, forceStream = true)
        val request = OllamaHttpSupport.buildJsonPost(endpoint, properties.requestTimeout, jsonBody)
        val response = OllamaHttpSupport.sendForStreamAndEnsureSuccess(http, request)
        streamProcessor.process(response.body(), onDelta, onDoneChunk)
    }

    private fun serializeEffectiveRequestJson(
        payload: OllamaChatRequest,
        forceStream: Boolean?,
    ): String =
        mapper.writeValueAsString(
            payload.copy(
                keepAlive = properties.keepAlive,
                stream = forceStream,
            ),
        )

    private fun parseAndValidateChatResponseString(body: String): OllamaChatResponse {
        val parsed = mapper.readValue(body, OllamaChatResponse::class.java)
        parsed.message?.content ?: throw OllamaInvalidResponseException(
            "Ollama response is missing `message.content`",
        )
        return parsed
    }
}
