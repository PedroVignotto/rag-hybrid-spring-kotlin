package dev.pedro.rag.infra.llm.ollama.errors

class OllamaHttpException(
    val status: Int,
    val responseBody: String,
) : RuntimeException("Ollama HTTP $status: $responseBody")

class OllamaInvalidResponseException(
    message: String,
) : RuntimeException(message)
