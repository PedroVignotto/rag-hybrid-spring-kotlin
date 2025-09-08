package dev.pedro.rag.infra.llm.ollama.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import dev.pedro.rag.infra.llm.ollama.model.response.OllamaChatStreamChunkResponse
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class NdjsonStreamProcessorTest {
    private val mapper = ObjectMapper().registerKotlinModule()
    private val sut = NdjsonStreamProcessor(mapper)

    @Test
    fun `should emit deltas in order and call onDoneChunk with metrics`() {
        val ndjson =
            listOf(
                """{"model":"llama3.2:3b","message":{"role":"assistant","content":"Hello"},"done":false}""",
                "",
                """{"model":"llama3.2:3b","message":{"role":"assistant","content":" world"},"done":false}""",
                """:heartbeat""",
                """{"done":true,"total_duration":123456789,"load_duration":987654,"prompt_eval_count":12,"eval_count":34}""",
            ).joinToString("\n")
        val input = ndjson.byteInputStream(StandardCharsets.UTF_8)
        val onDelta = mockk<(String) -> Unit>(relaxed = true)
        val onDoneChunk = mockk<(OllamaChatStreamChunkResponse) -> Unit>(relaxed = true)
        val doneSlot = slot<OllamaChatStreamChunkResponse>()

        sut.process(input = input, onDelta = onDelta, onDoneChunk = onDoneChunk)

        verifySequence {
            onDelta("Hello")
            onDelta(" world")
            onDoneChunk(capture(doneSlot))
        }
        val chunk = doneSlot.captured
        assertTrue(chunk.done)
        assertEquals(12, chunk.promptEvalCount)
        assertEquals(34, chunk.evalCount)
        assertEquals(123456789L, chunk.totalDuration)
        assertEquals(987654L, chunk.loadDuration)
    }

    @Test
    fun `should stop quietly when consumer cancels during onDelta`() {
        val ndjson =
            listOf(
                """{"message":{"role":"assistant","content":"first"},"done":false}""",
                """{"message":{"role":"assistant","content":"second"},"done":false}""",
                """{"done":true,"prompt_eval_count":1,"eval_count":2}""",
            ).joinToString("\n")
        val input = ByteArrayInputStream(ndjson.toByteArray(StandardCharsets.UTF_8))
        val onDelta = mockk<(String) -> Unit>()
        every { onDelta(any()) } just Runs andThenThrows RuntimeException("consumer canceled")
        val onDoneChunk = mockk<(OllamaChatStreamChunkResponse) -> Unit>(relaxed = true)

        sut.process(input = input, onDelta = onDelta, onDoneChunk = onDoneChunk)

        verifySequence {
            onDelta("first")
            onDelta("second")
        }
        verify(exactly = 0) { onDoneChunk(any()) }
    }

    @Test
    fun `should throw on invalid JSON line`() {
        val ndjson =
            listOf(
                """{"message":{"role":"assistant","content":"ok"},"done":false}""",
                """{invalid json""",
            ).joinToString("\n")
        val input = ByteArrayInputStream(ndjson.toByteArray(StandardCharsets.UTF_8))

        assertThrows<OllamaInvalidResponseException> {
            sut.process(input = input, onDelta = { }, onDoneChunk = { })
        }
    }

    @Test
    fun `should ignore blank and comment lines`() {
        val ndjson =
            listOf(
                "",
                """:keep-alive""",
                """{"message":{"role":"assistant","content":"A"},"done":false}""",
                "   ",
                """:another-comment""",
                """{"message":{"role":"assistant","content":"B"},"done":false}""",
                """{"done":true}""",
            ).joinToString("\n")
        val input = ByteArrayInputStream(ndjson.toByteArray(StandardCharsets.UTF_8))
        val onDelta = mockk<(String) -> Unit>(relaxed = true)
        val onDoneChunk = mockk<(OllamaChatStreamChunkResponse) -> Unit>(relaxed = true)

        sut.process(input = input, onDelta = onDelta, onDoneChunk = onDoneChunk)

        verifySequence {
            onDelta("A")
            onDelta("B")
            onDoneChunk(any())
        }
    }

    @Test
    fun `should emit deltas from response fallback and call done`() {
        val ndjson =
            listOf(
                """{"response":"X","done":false}""",
                """{"response":"Y","done":false}""",
                """{"done":true}""",
            ).joinToString("\n")
        val input = ByteArrayInputStream(ndjson.toByteArray(StandardCharsets.UTF_8))
        val onDelta = mockk<(String) -> Unit>(relaxed = true)
        val onDoneChunk = mockk<(OllamaChatStreamChunkResponse) -> Unit>(relaxed = true)

        sut.process(input = input, onDelta = onDelta, onDoneChunk = onDoneChunk)

        verifySequence {
            onDelta("X")
            onDelta("Y")
            onDoneChunk(any())
        }
    }
}
