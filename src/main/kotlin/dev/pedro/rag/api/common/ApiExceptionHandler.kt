package dev.pedro.rag.api.common

import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import dev.pedro.rag.infra.llm.ollama.errors.OllamaInvalidResponseException
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Any> {
        val errors =
            ex.bindingResult.allErrors.map { err ->
                val field = (err as? FieldError)?.field ?: err.objectName
                mapOf("field" to field, "message" to (err.defaultMessage ?: "invalid"))
            }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("errors" to errors))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<Any> {
        val errors =
            ex.constraintViolations.map { v ->
                mapOf("field" to v.propertyPath.toString(), "message" to (v.message ?: "invalid"))
            }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("errors" to errors))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "Malformed JSON request"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to (ex.message ?: "invalid argument")))

    @ExceptionHandler(OllamaInvalidResponseException::class)
    fun handleInvalidUpstream(ex: OllamaInvalidResponseException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(mapOf("error" to ex.message))

    @ExceptionHandler(OllamaHttpException::class)
    fun handleUpstreamHttp(ex: OllamaHttpException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            mapOf(
                "error" to "Upstream LLM error",
                "status" to ex.status,
                "upstreamBody" to ex.responseBody,
            ),
        )
}
