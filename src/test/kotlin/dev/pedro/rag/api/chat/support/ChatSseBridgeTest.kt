package dev.pedro.rag.api.chat.support

import com.fasterxml.jackson.databind.ObjectMapper
import dev.pedro.rag.api.chat.response.stream.ChatDeltaResponse
import dev.pedro.rag.api.chat.response.stream.ChatErrorResponse
import dev.pedro.rag.api.chat.response.stream.ChatUsageResponse
import dev.pedro.rag.application.chat.usecase.ChatUseCase
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatUsage
import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

@ExtendWith(MockKExtension::class)
class ChatSseBridgeTest(
    @param:MockK private val useCase: ChatUseCase,
    @param:MockK private val mapper: ObjectMapper,
    @param:MockK(relaxed = true) private val emitter: SseEmitter,
) {
    @InjectMockKs
    private lateinit var sut: ChatSseBridge

    @BeforeEach
    fun setUp() {
        every { emitter.send(any<SseEmitter.SseEventBuilder>()) } just runs
        every { emitter.complete() } just runs
        every { emitter.onTimeout(any()) } just runs
    }

    @Test
    fun `should emit delta, delta, usage, done and then complete`() {
        val input = ChatInput(messages = emptyList())
        every { useCase.handleStream(input, any(), any()) } answers {
            val onDelta = secondArg<(String) -> Unit>()
            val onUsage = thirdArg<((ChatUsage) -> Unit)?>()
            onDelta("he")
            onDelta("llo")
            onUsage?.invoke(ChatUsage(promptTokens = 10, completionTokens = 20, totalDurationMs = 5, loadDurationMs = 1))
        }
        every { mapper.writeValueAsString(ChatDeltaResponse("he")) } returns """{"delta":"he"}"""
        every { mapper.writeValueAsString(ChatDeltaResponse("llo")) } returns """{"delta":"llo"}"""
        every { mapper.writeValueAsString(ChatUsageResponse(10, 20, 5, 1)) } returns """{"usage":"ok"}"""
        every { mapper.writeValueAsString(emptyMap<String, String>()) } returns "{}"

        sut.stream(input, emitter)

        verifyOrder {
            emitter.onTimeout(any())
            useCase.handleStream(input, any(), any())
            mapper.writeValueAsString(ChatDeltaResponse("he"))
            emitter.send(any<SseEmitter.SseEventBuilder>())
            mapper.writeValueAsString(ChatDeltaResponse("llo"))
            emitter.send(any<SseEmitter.SseEventBuilder>())
            mapper.writeValueAsString(ChatUsageResponse(10, 20, 5, 1))
            emitter.send(any<SseEmitter.SseEventBuilder>())
            mapper.writeValueAsString(emptyMap<String, String>())
            emitter.send(any<SseEmitter.SseEventBuilder>())
            emitter.complete()
        }
    }

    @Test
    fun `should emit error with upstream info for OllamaHttpException`() {
        val input = ChatInput(messages = emptyList())
        val ex =
            mockk<OllamaHttpException> {
                every { status } returns 502
                every { responseBody } returns "upstream down"
            }
        every { useCase.handleStream(input, any(), any()) } throws ex
        every { mapper.writeValueAsString(ChatErrorResponse(status = 502, upstreamBody = "upstream down", message = null)) } returns
            """{"status":502,"upstream":"upstream down"}"""

        sut.stream(input, emitter)

        verifyOrder {
            emitter.onTimeout(any())
            useCase.handleStream(input, any(), any())
            mapper.writeValueAsString(ChatErrorResponse(status = 502, upstreamBody = "upstream down", message = null))
            emitter.send(any<SseEmitter.SseEventBuilder>())
            emitter.complete()
        }
    }

    @Test
    fun `should emit error 500 for generic exception`() {
        val input = ChatInput(messages = emptyList())
        every { useCase.handleStream(input, any(), any()) } throws RuntimeException("boom")
        every { mapper.writeValueAsString(match<ChatErrorResponse> { it.status == 500 && it.message?.contains("boom") == true }) } returns
            """{"status":500,"message":"boom"}"""

        sut.stream(input, emitter)

        verifyOrder {
            emitter.onTimeout(any())
            useCase.handleStream(input, any(), any())
            mapper.writeValueAsString(match<ChatErrorResponse> { it.status == 500 && it.message?.contains("boom") == true })
            emitter.send(any<SseEmitter.SseEventBuilder>())
            emitter.complete()
        }
    }

    @Test
    fun `should complete emitter if serialization fails`() {
        val input = ChatInput(messages = emptyList())
        every {
            useCase.handleStream(input, any(), any())
        } answers {
            val onDelta = secondArg<(String) -> Unit>()
            onDelta("token")
        }
        every { mapper.writeValueAsString(ChatDeltaResponse("token")) } throws IOException("broken")

        sut.stream(input, emitter)

        verify(exactly = 1) { useCase.handleStream(input, any(), any()) }
        verify { emitter.complete() }
    }

    @Test
    fun `should complete on timeout`() {
        val input = ChatInput(messages = emptyList())
        val timeoutSlot: CapturingSlot<Runnable> = slot()
        every { emitter.onTimeout(capture(timeoutSlot)) } just runs
        every { useCase.handleStream(input, any(), any()) } answers {}
        every { mapper.writeValueAsString(any<Any>()) } returns "{}"

        sut.stream(input, emitter)
        timeoutSlot.captured.run()

        verify(exactly = 1) { useCase.handleStream(input, any(), any()) }
        verify { emitter.complete() }
    }

    @Test
    fun `should run on provided executor and still emit events and complete`() {
        val input = ChatInput(messages = emptyList())
        val exec = java.util.concurrent.Executors.newSingleThreadExecutor()
        val sut = ChatSseBridge(useCase = useCase, mapper = mapper, executor = exec)
        every { useCase.handleStream(input, any(), any()) } answers {
            val onDelta = secondArg<(String) -> Unit>()
            val onUsage = thirdArg<((ChatUsage) -> Unit)?>()
            onDelta("hi")
            onUsage?.invoke(ChatUsage(promptTokens = 1, completionTokens = 2, totalDurationMs = 3, loadDurationMs = 4))
        }
        every { mapper.writeValueAsString(ChatDeltaResponse("hi")) } returns """{"delta":"hi"}"""
        every { mapper.writeValueAsString(ChatUsageResponse(1, 2, 3, 4)) } returns """{"usage":"ok"}"""
        every { mapper.writeValueAsString(emptyMap<String, String>()) } returns "{}"
        val latch = java.util.concurrent.CountDownLatch(1)
        every { emitter.complete() } answers {
            latch.countDown()
            Unit
        }

        sut.stream(input, emitter)

        kotlin.test.assertTrue(latch.await(2, java.util.concurrent.TimeUnit.SECONDS))
        verify(exactly = 1) { useCase.handleStream(input, any(), any()) }
        verify { emitter.complete() }
        exec.shutdown()
    }

    @Test
    fun `should emit error 500 with default message when throwable message is null`() {
        val input = ChatInput(messages = emptyList())
        every { useCase.handleStream(input, any(), any()) } throws RuntimeException()
        every {
            mapper.writeValueAsString(match<ChatErrorResponse> { it.status == 500 && it.message == "Internal error" })
        } returns """{"status":500,"message":"Internal error"}"""

        sut.stream(input, emitter)

        verifyOrder {
            emitter.onTimeout(any())
            useCase.handleStream(input, any(), any())
            mapper.writeValueAsString(match<ChatErrorResponse> { it.status == 500 && it.message == "Internal error" })
            emitter.send(any<SseEmitter.SseEventBuilder>())
            emitter.complete()
        }
    }
}
