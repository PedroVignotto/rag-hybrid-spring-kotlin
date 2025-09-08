package dev.pedro.rag.infra.llm.ollama.support

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatStreamChunkResponse
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class NdjsonStreamProcessor(
    private val mapper: ObjectMapper,
) {
    fun process(
        input: InputStream,
        onDelta: (String) -> Unit,
        onDoneChunk: ((OllamaChatStreamChunkResponse) -> Unit)? = null,
    ) {
        BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { br ->
            while (true) {
                val raw = br.readLine() ?: break
                val line = raw.trim()
                if (line.isEmpty() || isSkippableLine(line)) continue
                val chunk = parseChunk(line)
                if (chunk.done) {
                    onDoneChunk?.invoke(chunk)
                    break
                }
                val token = extractDelta(chunk) ?: continue
                if (!emitSafely(onDelta, token)) break
            }
        }
    }

    private fun isSkippableLine(line: String) = line.startsWith(":")

    private fun parseChunk(line: String): OllamaChatStreamChunkResponse =
        try {
            mapper.readValue(line, OllamaChatStreamChunkResponse::class.java)
        } catch (
            e: Exception,
        ) {
            throw OllamaInvalidResponseException("Invalid Ollama stream line: ${e.message}")
        }

    private fun extractDelta(chunk: OllamaChatStreamChunkResponse): String? = chunk.message?.content ?: chunk.response

    private fun emitSafely(
        onDelta: (String) -> Unit,
        token: String,
    ): Boolean =
        try {
            onDelta(token)
            true
        } catch (_: Exception) {
            false
        }
}
