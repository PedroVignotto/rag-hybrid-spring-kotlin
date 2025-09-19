package dev.pedro.rag.infra.llm.ollama.embedding.client

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.config.llm.LlmProperties
import dev.pedro.rag.infra.llm.ollama.embedding.request.OllamaEmbeddingRequest
import dev.pedro.rag.infra.llm.ollama.embedding.response.OllamaEmbeddingResponse
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.support.OllamaHttpSupport
import java.net.URI
import java.net.http.HttpClient

class OllamaEmbeddingHttpClient(
    private val http: HttpClient,
    private val mapper: ObjectMapper,
    private val properties: LlmProperties.Ollama,
) {
    private val endpoint: URI =
        OllamaHttpSupport.endpoint(properties.baseUrl, "/api/embeddings")

    fun embed(
        model: String,
        inputs: List<String>,
    ): List<FloatArray> {
        validateInputs(inputs)
        val jsonBody = serializeRequest(model, inputs)
        val request = OllamaHttpSupport.buildJsonPost(endpoint, properties.requestTimeout, jsonBody)
        val httpResponse = OllamaHttpSupport.sendForStringAndEnsureSuccess(http, request)
        val response = parseResponse(httpResponse.body())
        val batchVectors = toBatchVectors(response)
        validateBatchSize(batchVectors, expected = inputs.size)
        return batchVectors.map(::toFloatArray32)
    }

    private fun validateInputs(inputs: List<String>) {
        require(inputs.isNotEmpty()) { "inputs must not be empty" }
        require(inputs.all { it.isNotBlank() }) { "inputs must not contain blank strings" }
    }

    private fun serializeRequest(
        model: String,
        inputs: List<String>,
    ): String = mapper.writeValueAsString(OllamaEmbeddingRequest(model = model, input = inputs))

    private fun parseResponse(body: String): OllamaEmbeddingResponse =
        try {
            mapper.readValue(body, OllamaEmbeddingResponse::class.java)
        } catch (_: Exception) {
            throw OllamaInvalidResponseException("Invalid JSON from Ollama embeddings endpoint")
        }

    private fun toBatchVectors(res: OllamaEmbeddingResponse): List<List<Double>> =
        when {
            res.embeddings != null -> res.embeddings
            res.embedding != null -> listOf(res.embedding)
            else -> throw OllamaInvalidResponseException(
                "Ollama embedding response is missing 'embeddings' or 'embedding'",
            )
        }

    private fun validateBatchSize(
        vectors: List<List<Double>>,
        expected: Int,
    ) {
        if (vectors.size != expected) {
            throw OllamaInvalidResponseException(
                "Ollama embedding response size mismatch: expected $expected, got ${vectors.size}",
            )
        }
    }

    private fun toFloatArray32(src: List<Double>): FloatArray {
        val out = FloatArray(src.size)
        for (i in src.indices) out[i] = src[i].toFloat()
        return out
    }
}
