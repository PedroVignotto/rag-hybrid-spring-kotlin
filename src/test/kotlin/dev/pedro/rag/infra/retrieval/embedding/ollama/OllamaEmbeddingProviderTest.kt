package dev.pedro.rag.infra.retrieval.embedding.ollama

import dev.pedro.rag.domain.retrieval.EmbeddingSpec
import dev.pedro.rag.infra.llm.ollama.embedding.client.OllamaEmbeddingHttpClient
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class OllamaEmbeddingProviderTest {
    companion object {
        private const val MODEL = "mxbai-embed-large"
        private const val DIMENSION = 3
        private val SPEC = EmbeddingSpec(provider = "ollama", model = MODEL, dim = DIMENSION, normalized = false)
    }

    @MockK
    private lateinit var client: OllamaEmbeddingHttpClient

    private lateinit var sut: OllamaEmbeddingProvider

    @BeforeEach
    fun setUp() {
        sut = OllamaEmbeddingProvider(client = client, embeddingSpec = SPEC)
    }

    @Test
    fun `should expose spec as provided`() {
        val out = sut.spec()

        assertThat(out).isEqualTo(SPEC)
        confirmVerified(client)
    }

    @Test
    fun `should embedAll via client and map FloatArray to EmbeddingVector`() {
        val texts = listOf("hello", "world")
        val vectors = listOf(floatArrayOf(0.1f, 0.2f, 0.3f), floatArrayOf(0.4f, 0.5f, 0.6f))
        every { client.embed(model = MODEL, inputs = texts) } returns vectors

        val out = sut.embedAll(texts)

        assertThat(out).hasSize(vectors.size)
        out.zip(vectors).forEach { (vec, expected) ->
            assertThat(vec.values.toList()).containsExactlyElementsOf(expected.toList())
            assertThat(vec.dim).isEqualTo(DIMENSION)
            assertThat(vec.normalized).isFalse()
        }
        verify(exactly = 1) { client.embed(model = MODEL, inputs = texts) }
        confirmVerified(client)
    }

    @Test
    fun `should embed single text by delegating to batch call`() {
        val text = "only-one"
        every { client.embed(model = MODEL, inputs = listOf(text)) } returns listOf(floatArrayOf(0.9f, 1.1f, 1.3f))

        val out = sut.embed(text)

        assertThat(out.values.toList()).containsExactly(0.9f, 1.1f, 1.3f)
        assertThat(out.dim).isEqualTo(DIMENSION)
        assertThat(out.normalized).isFalse()
        verify(exactly = 1) { client.embed(model = MODEL, inputs = listOf(text)) }
        confirmVerified(client)
    }

    @Test
    fun `should return empty list when texts is empty`() {
        val out = sut.embedAll(emptyList())

        assertThat(out).isEmpty()
        verify(exactly = 0) { client.embed(any(), any()) }
        confirmVerified(client)
    }

    @Test
    fun `should throw when vector dimension differs from spec`() {
        val texts = listOf("bad-dim")
        every { client.embed(model = MODEL, inputs = texts) } returns listOf(floatArrayOf(0.1f, 0.2f))

        val exception = assertThrows(IllegalArgumentException::class.java) { sut.embedAll(texts) }
        assertThat(exception).hasMessageContaining("Embedding dimension mismatch")
        verify(exactly = 1) { client.embed(model = MODEL, inputs = texts) }
        confirmVerified(client)
    }
}
