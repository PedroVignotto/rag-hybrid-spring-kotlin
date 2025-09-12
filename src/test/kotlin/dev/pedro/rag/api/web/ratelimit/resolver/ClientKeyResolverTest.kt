package dev.pedro.rag.api.web.ratelimit.resolver

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class ClientKeyResolverTest {
    private val sut = ClientKeyResolver()

    @Test
    fun `should resolve first IP from X-Forwarded-For (trimmed)`() {
        val request =
            MockHttpServletRequest().apply {
                addHeader("X-Forwarded-For", " 198.51.100.1 , 203.0.113.2 ")
                remoteAddr = "10.0.0.1"
            }

        val key = sut.resolve(request)

        Assertions.assertThat(key).isEqualTo("198.51.100.1")
    }

    @Test
    fun `should fallback to remoteAddr when X-Forwarded-For is missing or blank`() {
        val request =
            MockHttpServletRequest().apply {
                remoteAddr = "192.0.2.10"
            }

        val key = sut.resolve(request)

        Assertions.assertThat(key).isEqualTo("192.0.2.10")
    }

    @Test
    fun `should return unknown when neither header nor remoteAddr are present`() {
        val request = mockk<HttpServletRequest>()
        every { request.getHeader("X-Forwarded-For") } returns null
        every { request.remoteAddr } returns null

        val key = sut.resolve(request)

        Assertions.assertThat(key).isEqualTo("unknown")
    }

    @Test
    fun `should fallback to remoteAddr when X-Forwarded-For is blank`() {
        val req =
            MockHttpServletRequest().apply {
                addHeader("X-Forwarded-For", "   ")
                remoteAddr = "203.0.113.9"
            }

        val key = sut.resolve(req)

        Assertions.assertThat(key).isEqualTo("203.0.113.9")
    }

    @Test
    fun `should fallback to remoteAddr when first X-Forwarded-For element is empty`() {
        val req =
            MockHttpServletRequest().apply {
                addHeader("X-Forwarded-For", ", 198.51.100.2")
                remoteAddr = "203.0.113.10"
            }

        val key = sut.resolve(req)

        Assertions.assertThat(key).isEqualTo("203.0.113.10")
    }

    @Test
    fun `should support IPv6 address from X-Forwarded-For`() {
        val req =
            MockHttpServletRequest().apply {
                addHeader("X-Forwarded-For", "2001:db8::1, 198.51.100.3")
                remoteAddr = "203.0.113.11"
            }

        val key = sut.resolve(req)

        Assertions.assertThat(key).isEqualTo("2001:db8::1")
    }
}
