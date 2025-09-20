package dev.pedro.rag.infra.llm.ollama.embedding.client

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.config.llm.LlmProperties
import dev.pedro.rag.infra.llm.ollama.embedding.response.OllamaEmbeddingResponse
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.support.OllamaHttpSupport
import java.net.URI
import java.net.http.HttpClient
import java.util.concurrent.StructuredTaskScope

class OllamaEmbeddingHttpClient(
    private val http: HttpClient,
    private val mapper: ObjectMapper,
    private val properties: LlmProperties.Ollama,
) {
    companion object {
        private const val MAX_PARALLEL_EMBEDS = 16
    }

    private val embedEndpoint: URI =
        OllamaHttpSupport.endpoint(properties.baseUrl, "/api/embed")

    fun embed(
        model: String,
        inputs: List<String>,
    ): List<FloatArray> {
        requireValidInputs(inputs)
        return if (inputs.size == 1) {
            listOf(embedOne(model, inputs[0]))
        } else {
            embedManyPreservingOrder(model, inputs)
        }
    }

    private fun embedOne(
        model: String,
        input: String,
    ): FloatArray {
        val response = postSingleAndParse(model, input)
        val vector = extractVectorOrThrow(response)
        val out = toFloatArray32(vector)
        ensureNonEmpty(out)
        return out
    }

    private fun postSingleAndParse(
        model: String,
        input: String,
    ): OllamaEmbeddingResponse {
        val jsonBody = serializeSingleRequest(model, input)
        val request = OllamaHttpSupport.buildJsonPost(embedEndpoint, properties.requestTimeout, jsonBody)
        val response = OllamaHttpSupport.sendForStringAndEnsureSuccess(http, request)
        return parseResponse(response.body())
    }

    private fun serializeSingleRequest(
        model: String,
        input: String,
    ): String = mapper.writeValueAsString(mapOf("model" to model, "input" to input))

    private fun extractVectorOrThrow(res: OllamaEmbeddingResponse): List<Double> =
        when {
            !res.error.isNullOrBlank() -> throw OllamaInvalidResponseException("Ollama error: ${res.error}")
            res.embedding != null -> res.embedding
            res.embeddings?.isNotEmpty() == true -> res.embeddings.first()
            else -> throw OllamaInvalidResponseException("Ollama embedding response is missing 'embedding' or 'embeddings'")
        }

    private fun embedManyPreservingOrder(
        model: String,
        inputs: List<String>,
    ): List<FloatArray> {
        val slots: Array<FloatArray?> = arrayOfNulls(inputs.size)
        inputs.withIndex().chunked(MAX_PARALLEL_EMBEDS).forEach { window ->
            StructuredTaskScope.ShutdownOnFailure().use { scope ->
                window.forEach { (idx, text) ->
                    scope.fork {
                        slots[idx] = embedOne(model, text)
                    }
                }
                scope.join()
                scope.throwIfFailed()
            }
        }
        return toOrderedVectorsOrThrow(slots)
    }

    private fun toOrderedVectorsOrThrow(slots: Array<FloatArray?>): List<FloatArray> =
        slots.mapIndexed { i, vec ->
            vec ?: throw OllamaInvalidResponseException("Missing embedding result at index=$i")
        }

    private fun requireValidInputs(inputs: List<String>) {
        require(inputs.isNotEmpty()) { "inputs must not be empty" }
        require(inputs.all { it.isNotBlank() }) { "inputs must not contain blank strings" }
    }

    private fun parseResponse(body: String): OllamaEmbeddingResponse =
        try {
            mapper.readValue(body, OllamaEmbeddingResponse::class.java)
        } catch (_: Exception) {
            throw OllamaInvalidResponseException("Invalid JSON from Ollama embeddings endpoint")
        }

    private fun toFloatArray32(src: List<Double>): FloatArray {
        val out = FloatArray(src.size)
        for (i in src.indices) out[i] = src[i].toFloat()
        return out
    }

    private fun ensureNonEmpty(vec: FloatArray) {
        if (vec.isEmpty()) {
            throw OllamaInvalidResponseException("Ollama returned empty embedding vector")
        }
    }
}
