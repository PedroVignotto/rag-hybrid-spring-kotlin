package dev.pedro.rag.api.chat.common

import dev.pedro.rag.api.common.ApiExceptionHandler
import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.core.MethodParameter

class ApiExceptionHandlerTest {

    private val sut = ApiExceptionHandler()

    @Test
    fun `should format validation errors with field and object errors`() {
        val target = Any()
        val binding = BeanPropertyBindingResult(target, "req")
        binding.addError(FieldError("req", "name", "must not be blank"))
        binding.addError(ObjectError("req", "global invalid"))
        val methodParam = mockk<MethodParameter>(relaxed = true)
        val exception = MethodArgumentNotValidException(methodParam, binding)

        val response = sut.handleValidation(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val body = response.body as Map<*, *>
        val errors = (body["errors"] as List<*>).map { it as Map<*, *> }
        assertThat(errors).hasSize(2)
        assertThat(errors.any { it["field"] == "name" && it["message"] == "must not be blank" }).isTrue()
        assertThat(errors.any { it["field"] == "req" && it["message"] == "global invalid" }).isTrue()
    }

    @Test
    fun `should return 400 with Malformed JSON message on unreadable body`() {
        val exception = mockk<HttpMessageNotReadableException>(relaxed = true)

        val response = sut.handleUnreadable(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val body = response.body as Map<*, *>
        assertThat(body["error"]).isEqualTo("Malformed JSON request")
    }

    @Test
    fun `should return 400 with message on IllegalArgumentException`() {
        val response = sut.handleIllegalArgument(IllegalArgumentException("bad param"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val body = response.body as Map<*, *>
        assertThat(body["error"]).isEqualTo("bad param")
    }

    @Test
    fun `should map invalid upstream to 502 with message`() {
        val exception = mockk<OllamaInvalidResponseException>()
        every { exception.message } returns "invalid upstream payload"

        val response = sut.handleInvalidUpstream(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        val body = response.body as Map<*, *>
        assertThat(body["error"]).isEqualTo("invalid upstream payload")
    }

    @Test
    fun `should map upstream http error to 502 with status and body`() {
        val exception = mockk<OllamaHttpException>()
        every { exception.status } returns 502
        every { exception.responseBody } returns "gateway down"

        val response = sut.handleUpstreamHttp(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        val body = response.body as Map<*, *>
        assertThat(body["error"]).isEqualTo("Upstream LLM error")
        assertThat(body["status"]).isEqualTo(502)
        assertThat(body["upstreamBody"]).isEqualTo("gateway down")
    }
}