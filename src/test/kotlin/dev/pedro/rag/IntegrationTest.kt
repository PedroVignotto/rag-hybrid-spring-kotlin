package dev.pedro.rag

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTest {
    companion object {
        private val server = MockWebServer()

        @JvmStatic @BeforeAll
        fun startUpstream() {
            server.start(18081)
        }

        @JvmStatic @AfterAll
        fun stopUpstream() {
            server.shutdown()
        }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProps(reg: DynamicPropertyRegistry) {
            reg.add("llm.ollama.base-url") { "http://localhost:18081/" }
        }
    }

    @Autowired protected lateinit var mvc: MockMvc

    @Autowired protected lateinit var mapper: ObjectMapper

    protected fun enqueueUpstreamJson(
        body: Any,
        status: Int = 200,
    ) {
        val json = mapper.writeValueAsString(body)
        enqueueUpstream(
            MockResponse().setResponseCode(status).setBody(json)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE),
        )
    }

    protected fun enqueueUpstream(response: MockResponse) {
        Companion.server.enqueue(response)
    }

    protected fun takeUpstreamRequest(): RecordedRequest = Companion.server.takeRequest()
}
