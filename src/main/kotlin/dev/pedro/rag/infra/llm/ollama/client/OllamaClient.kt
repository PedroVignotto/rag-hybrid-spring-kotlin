package dev.pedro.rag.infra.llm.ollama.client

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.config.LlmProperties
import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.model.request.OllamaChatRequest
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OllamaClient(
    private val http: HttpClient,
    private val mapper: ObjectMapper,
    private val properties: LlmProperties.Ollama,
) {
    private val endpoint: URI = URI.create(properties.baseUrl.toString().trimEnd('/') + "/api/chat")
    private val requestTimeout: Duration = properties.requestTimeout
    private val writer = mapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL).writer()

    fun chat(payload: OllamaChatRequest): OllamaChatResponse {
        val jsonBody = buildJsonBody(payload, forceStream = null)
        val httpRequest = buildHttpRequestForChat(jsonBody)
        val httpResponse = executeHttpRequest(httpRequest)
        return parseChatResponse(httpResponse)
    }

    private fun buildJsonBody(
        payload: OllamaChatRequest,
        forceStream: Boolean?,
    ): String {
        val effective = payload.copy(keepAlive = properties.keepAlive, stream = forceStream)
        return writer.writeValueAsString(effective)
    }

    private fun buildHttpRequestForChat(jsonBody: String): HttpRequest =
        HttpRequest.newBuilder()
            .uri(endpoint)
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

    private fun executeHttpRequest(request: HttpRequest): HttpResponse<String> = http.send(request, HttpResponse.BodyHandlers.ofString())

    private fun parseChatResponse(res: HttpResponse<String>): OllamaChatResponse {
        if (res.statusCode() !in 200..299) {
            throw OllamaHttpException(res.statusCode(), res.body())
        }
        val parsed = mapper.readValue(res.body(), OllamaChatResponse::class.java)
        parsed.message?.content ?: throw OllamaInvalidResponseException(
            "Ollama response is missing `message.content`",
        )
        return parsed
    }
}
