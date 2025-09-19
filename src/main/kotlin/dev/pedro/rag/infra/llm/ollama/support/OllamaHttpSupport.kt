package dev.pedro.rag.infra.llm.ollama.support

import dev.pedro.rag.infra.llm.ollama.errors.OllamaHttpException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

object OllamaHttpSupport {
    fun endpoint(
        baseUrl: URI,
        path: String,
    ): URI {
        val base = baseUrl.toString().trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        return URI.create(base + p)
    }

    fun buildJsonPost(
        uri: URI,
        timeout: Duration,
        jsonBody: String,
    ): HttpRequest =
        HttpRequest.newBuilder()
            .uri(uri)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build()

    fun sendForStringAndEnsureSuccess(
        http: HttpClient,
        req: HttpRequest,
    ): HttpResponse<String> {
        val res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (!is2xx(res.statusCode())) throw OllamaHttpException(res.statusCode(), res.body())
        return res
    }

    fun sendForStreamAndEnsureSuccess(
        http: HttpClient,
        req: HttpRequest,
    ): HttpResponse<InputStream> {
        val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
        if (!is2xx(res.statusCode())) {
            val err = res.body().readAllBytes().toString(StandardCharsets.UTF_8)
            throw OllamaHttpException(res.statusCode(), err)
        }
        return res
    }

    private fun is2xx(code: Int) = code in 200..299
}
