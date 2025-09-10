package dev.pedro.rag.infra.llm.ollama.client

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.config.llm.LlmProperties
import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatResponse
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatStreamChunkResponse
import dev.pedro.rag.infra.llm.ollama.support.NdjsonStreamProcessor
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class OllamaClient(
    private val http: HttpClient,
    private val mapper: ObjectMapper,
    private val properties: LlmProperties.Ollama,
    private val streamProcessor: NdjsonStreamProcessor,
) {
    private val endpoint: URI = URI.create(properties.baseUrl.toString().trimEnd('/') + "/api/chat")
    private val writer =
        mapper.copy()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .writer()

    fun chat(payload: OllamaChatRequest): OllamaChatResponse {
        val jsonBody = serializeEffectiveRequestJson(payload, forceStream = null)
        val request = buildChatHttpRequest(jsonBody)
        val response = sendForStringAndEnsureSuccess(request)
        return parseAndValidateChatResponseString(response.body())
    }

    fun chatStream(
        payload: OllamaChatRequest,
        onDelta: (String) -> Unit,
        onDoneChunk: ((OllamaChatStreamChunkResponse) -> Unit)? = null,
    ) {
        val jsonBody = serializeEffectiveRequestJson(payload, forceStream = true)
        val request = buildChatHttpRequest(jsonBody)
        val response = sendForStreamAndEnsureSuccess(request)
        streamProcessor.process(response.body(), onDelta, onDoneChunk)
    }

    private fun serializeEffectiveRequestJson(
        payload: OllamaChatRequest,
        forceStream: Boolean?,
    ): String =
        writer.writeValueAsString(
            payload.copy(
                keepAlive = properties.keepAlive,
                stream = forceStream,
            ),
        )

    private fun buildChatHttpRequest(jsonBody: String): HttpRequest =
        HttpRequest.newBuilder()
            .uri(endpoint)
            .timeout(properties.requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build()

    private fun sendForStringAndEnsureSuccess(req: HttpRequest): HttpResponse<String> {
        val res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (!is2xx(res.statusCode())) throw OllamaHttpException(res.statusCode(), res.body())
        return res
    }

    private fun sendForStreamAndEnsureSuccess(req: HttpRequest): HttpResponse<InputStream> {
        val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
        if (!is2xx(res.statusCode())) {
            val err = res.body().readAllBytes().toString(StandardCharsets.UTF_8)
            throw OllamaHttpException(res.statusCode(), err)
        }
        return res
    }

    private fun parseAndValidateChatResponseString(body: String): OllamaChatResponse {
        val parsed = mapper.readValue(body, OllamaChatResponse::class.java)
        parsed.message?.content ?: throw OllamaInvalidResponseException(
            "Ollama response is missing `message.content`",
        )
        return parsed
    }

    private fun is2xx(code: Int) = code in 200..299
}
