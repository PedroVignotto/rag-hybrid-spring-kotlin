package dev.pedro.rag.application.retrieval.ask.usecase

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.application.retrieval.ask.context.BuiltContext
import dev.pedro.rag.application.retrieval.ask.context.CitationIndex
import dev.pedro.rag.application.retrieval.ask.context.ContextBuilder
import dev.pedro.rag.application.retrieval.ask.context.ContextSource
import dev.pedro.rag.application.retrieval.ask.dto.AskInput
import dev.pedro.rag.application.retrieval.ask.dto.Citation
import dev.pedro.rag.application.retrieval.ask.i18n.AskLocalization
import dev.pedro.rag.application.retrieval.ask.parsing.citation.CitationMapper
import dev.pedro.rag.application.retrieval.ask.parsing.output.OutputParser
import dev.pedro.rag.application.retrieval.ask.parsing.output.ParsedOutput
import dev.pedro.rag.application.retrieval.ask.prompt.PromptBuilder
import dev.pedro.rag.application.retrieval.ask.prompt.PromptPayload
import dev.pedro.rag.application.retrieval.ask.selection.ContextSelector
import dev.pedro.rag.application.retrieval.search.dto.SearchInput
import dev.pedro.rag.application.retrieval.search.dto.SearchOutput
import dev.pedro.rag.application.retrieval.search.usecase.SearchUseCase
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatOutput
import dev.pedro.rag.domain.retrieval.DocumentId
import dev.pedro.rag.domain.retrieval.SearchMatch
import dev.pedro.rag.domain.retrieval.TextChunk
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(MockKExtension::class)
class DefaultAskUseCaseTest {

    @MockK lateinit var searchUseCase: SearchUseCase
    @MockK lateinit var selector: ContextSelector
    @MockK lateinit var contextBuilder: ContextBuilder
    @MockK lateinit var promptBuilder: PromptBuilder
    @MockK lateinit var chatPort: LlmChatPort
    @MockK lateinit var outputParser: OutputParser
    @MockK lateinit var citationMapper: CitationMapper
    @MockK lateinit var askLocalization: AskLocalization

    private fun sut() = DefaultAskUseCase(
        searchUseCase = searchUseCase,
        selector = selector,
        contextBuilder = contextBuilder,
        promptBuilder = promptBuilder,
        chatPort = chatPort,
        outputParser = outputParser,
        citationMapper = citationMapper,
        askLocalization = askLocalization,
        poolTopK = 12,
        maxChunksPerDoc = 2,
        budgetChars = 3_000
    )

    @Test
    fun `happy path returns parsed answer and mapped citations`() {
        every { searchUseCase.search(any<SearchInput>()) } returns SearchOutput(
            matches = listOf(
                match("ze-menu-001", "Tem opção vegetariana e aceita Pix.", 0.91),
                match("ze-promo-001", "Terças 2x1 no smash.", 0.84),
                match("ze-hours-001", "Domingo 12:00–22:30.", 0.80),
            )
        )
        val selected = listOf(
            src("ze-menu-001", "ze-menu-001", 0, "Tem opção vegetariana e aceita Pix.", 0.91),
            src("ze-promo-001", "ze-promo-001", 1, "Terças 2x1 no smash.", 0.84),
        )
        every { selector.select(any(), topK = 10, maxChunksPerDoc = 2) } returns selected
        val built = BuiltContext(
            text = "[1] Tem opção vegetariana e aceita Pix.\n\n[2] Terças 2x1 no smash.",
            index = listOf(
                CitationIndex(1, "ze-menu-001", "ze-menu-001", 0),
                CitationIndex(2, "ze-promo-001", "ze-promo-001", 1),
            ),
            usedK = 2,
            truncated = false
        )
        every { contextBuilder.build(selected, 3_000) } returns built
        every { promptBuilder.build(built, "tem veggie? promoção", "pt-BR") } returns
                PromptPayload(system = "SYS", user = "USER")
        every { chatPort.complete(any<ChatInput>()) } returns ChatOutput(
            """
            ANSWER:
            Sim, há veggie e promoção. [1][2]
            
            CITATIONS:
            [1] ze-menu-001#chunk0
            [2] ze-promo-001#chunk1
            """.trimIndent()
        )
        every { outputParser.parse(any()) } returns ParsedOutput(
            answer = "Sim, há veggie e promoção. [1][2]",
            citationNs = listOf(1, 2)
        )
        every { citationMapper.map(listOf(1, 2), built) } returns listOf(
            Citation("ze-menu-001", "ze-menu-001", 0),
            Citation("ze-promo-001", "ze-promo-001", 1),
        )

        val result = sut().handle(AskInput(query = "tem veggie? promoção", topK = 10, filter = emptyMap(), lang = "pt-BR"))

        assertThat(result.answer).contains("veggie")
        assertThat(result.citations.map { it.documentId }).containsExactly("ze-menu-001", "ze-promo-001")
        assertThat(result.usedK).isEqualTo(2)
        assertThat(result.notes).isNull()
        verify { selector.select(any(), topK = 10, maxChunksPerDoc = 2) }
        verify { contextBuilder.build(selected, 3_000) }
        verify { promptBuilder.build(built, "tem veggie? promoção", "pt-BR") }
        verify { outputParser.parse(any()) }
        verify { citationMapper.map(listOf(1, 2), built) }
    }

    @Test
    fun `returns no-matches when search has no results`() {
        every { searchUseCase.search(any<SearchInput>()) } returns SearchOutput(matches = emptyList())
        every { askLocalization.noContext("pt-BR") } returns "PT-SEM-CONTEXTO"

        val result = sut().handle(AskInput(query = "x", topK = 10, filter = emptyMap(), lang = "pt-BR"))

        assertThat(result.answer).isEqualTo("PT-SEM-CONTEXTO")
        assertThat(result.citations).isEmpty()
        assertThat(result.usedK).isEqualTo(0)
        assertThat(result.notes).isEqualTo("no-matches")
        verify(exactly = 0) { selector.select(any(), any(), any()) }
        verify(exactly = 0) { contextBuilder.build(any(), any()) }
        verify(exactly = 0) { promptBuilder.build(any(), any(), any()) }
        verify(exactly = 0) { chatPort.complete(any()) }
        verify(exactly = 0) { outputParser.parse(any()) }
        verify(exactly = 0) { citationMapper.map(any(), any()) }
    }

    @Test
    fun `falls back to extractive when llm returns blank`() {
        every { searchUseCase.search(any<SearchInput>()) } returns SearchOutput(
            matches = listOf(
                match("ze-menu-001", "Tem opção vegetariana e aceita Pix.", 0.91),
                match("ze-promo-001", "Terças 2x1 no smash.", 0.84),
            )
        )
        val selected = listOf(
            src("ze-menu-001", "ze-menu-001", 0, "Tem opção vegetariana e aceita Pix.", 0.91),
            src("ze-promo-001", "ze-promo-001", 1, "Terças 2x1 no smash.", 0.84),
        )
        every { selector.select(any(), topK = 10, maxChunksPerDoc = 2) } returns selected
        every { contextBuilder.build(selected, 3_000) } returns BuiltContext(
            text = "[1] Tem opção vegetariana e aceita Pix.\n\n[2] Terças 2x1 no smash.",
            index = listOf(
                CitationIndex(1, "ze-menu-001", "ze-menu-001", 0),
                CitationIndex(2, "ze-promo-001", "ze-promo-001", 1),
            ),
            usedK = 2,
            truncated = false
        )
        every { promptBuilder.build(any(), any(), any()) } returns PromptPayload("S", "U")
        every { chatPort.complete(any()) } returns ChatOutput("")

        val result = sut().handle(AskInput(query = "tem veggie e promo?", topK = 10, filter = emptyMap(), lang = "pt-BR"))

        assertThat(result.notes).isEqualTo("extractive-fallback")
        assertThat(result.usedK).isEqualTo(2)
        assertThat(result.citations.map { it.documentId }).containsExactly("ze-menu-001", "ze-promo-001")
        assertThat(result.answer).contains("[1] Tem opção vegetariana")
        assertThat(result.answer).contains("[2] Terças 2x1")
    }

    @Test
    fun `sets llm-no-citations note when parser returns empty Ns but context existed`() {
        every { searchUseCase.search(any<SearchInput>()) } returns SearchOutput(
            matches = listOf(match("ze-menu-001", "Tem opção vegetariana e aceita Pix.", 0.91))
        )
        val selected = listOf(src("ze-menu-001", "ze-menu-001", 0, "Tem opção vegetariana e aceita Pix.", 0.91))
        every { selector.select(any(), topK = 10, maxChunksPerDoc = 2) } returns selected
        val built = BuiltContext(
            text = "[1] Tem opção vegetariana e aceita Pix.",
            index = listOf(CitationIndex(1, "ze-menu-001", "ze-menu-001", 0)),
            usedK = 1,
            truncated = false
        )
        every { contextBuilder.build(selected, 3_000) } returns built
        every { promptBuilder.build(any(), any(), any()) } returns PromptPayload("S", "U")
        every { chatPort.complete(any()) } returns ChatOutput("ANSWER:\nSim.\n\nCITATIONS:\n")
        every { outputParser.parse(any()) } returns ParsedOutput(answer = "Sim.", citationNs = emptyList())
        every { citationMapper.map(emptyList(), built) } returns emptyList()

        val result = sut().handle(AskInput(query = "tem veggie?", topK = 10, filter = emptyMap(), lang = "pt-BR"))

        assertThat(result.answer).isEqualTo("Sim.")
        assertThat(result.citations).isEmpty()
        assertThat(result.usedK).isEqualTo(1)
        assertThat(result.notes).isEqualTo("llm-no-citations")
    }

    @Test
    fun `returns no-matches when search throws`() {
        every { searchUseCase.search(any<SearchInput>()) } throws RuntimeException("boom")
        every { askLocalization.noContext("pt-BR") } returns "PT-SEM-CONTEXTO"

        val result = sut().handle(AskInput(query = "x", topK = 10, filter = emptyMap(), lang = "pt-BR"))

        assertThat(result.answer).isEqualTo("PT-SEM-CONTEXTO")
        assertThat(result.usedK).isEqualTo(0)
        assertThat(result.citations).isEmpty()
        assertThat(result.notes).isEqualTo("no-matches")
        verify(exactly = 0) { selector.select(any(), any(), any()) }
        verify(exactly = 0) { contextBuilder.build(any(), any()) }
        verify(exactly = 0) { promptBuilder.build(any(), any(), any()) }
        verify(exactly = 0) { chatPort.complete(any()) }
        verify(exactly = 0) { outputParser.parse(any()) }
        verify(exactly = 0) { citationMapper.map(any(), any()) }
    }

    @Test
    fun `falls back to extractive when llm throws exception`() {
        every { searchUseCase.search(any<SearchInput>()) } returns SearchOutput(
            matches = listOf(
                match("ze-menu-001", "Tem opção vegetariana e aceita Pix.", 0.91),
                match("ze-promo-001", "Terças 2x1 no smash.", 0.84),
            )
        )
        val selected = listOf(
            src("ze-menu-001", "ze-menu-001", 0, "Tem opção vegetariana e aceita Pix.", 0.91),
            src("ze-promo-001", "ze-promo-001", 1, "Terças 2x1 no smash.", 0.84),
        )
        every { selector.select(any(), topK = 10, maxChunksPerDoc = 2) } returns selected
        every { contextBuilder.build(selected, 3_000) } returns BuiltContext(
            text = "[1] A\n\n[2] B",
            index = listOf(
                CitationIndex(1, "ze-menu-001", "ze-menu-001", 0),
                CitationIndex(2, "ze-promo-001", "ze-promo-001", 1),
            ),
            usedK = 2,
            truncated = false
        )
        every { promptBuilder.build(any(), any(), any()) } returns PromptPayload("S", "U")
        every { chatPort.complete(any()) } throws RuntimeException("llm down")

        val result = sut().handle(AskInput(query = "q", topK = 10, filter = emptyMap(), lang = "pt-BR"))

        assertThat(result.notes).isEqualTo("extractive-fallback")
        assertThat(result.usedK).isEqualTo(2)
        assertThat(result.citations.map { it.documentId }).containsExactly("ze-menu-001", "ze-promo-001")
        verify(exactly = 0) { outputParser.parse(any()) }
        verify(exactly = 0) { citationMapper.map(any(), any()) }
    }

    @Test
    fun `notes is null when no citations and usedK equals 0`() {
        every { searchUseCase.search(any<SearchInput>()) } returns SearchOutput(
            matches = listOf(match("ze-menu-001", "x", 0.9))
        )
        every { selector.select(any(), topK = 10, maxChunksPerDoc = 2) } returns emptyList()
        every { contextBuilder.build(emptyList(), 3_000) } returns BuiltContext(
            text = "",
            index = emptyList(),
            usedK = 0,
            truncated = false
        )
        every { promptBuilder.build(any(), any(), any()) } returns PromptPayload("S", "U")
        every { chatPort.complete(any()) } returns ChatOutput("ANSWER:\nOk\n\nCITATIONS:\n")
        every { outputParser.parse(any()) } returns ParsedOutput(answer = "Ok", citationNs = emptyList())
        every { citationMapper.map(emptyList(), any()) } returns emptyList()

        val result = sut().handle(AskInput(query = "q", topK = 10, filter = emptyMap(), lang = "pt-BR"))

        assertThat(result.answer).isEqualTo("Ok")
        assertThat(result.usedK).isEqualTo(0)
        assertThat(result.citations).isEmpty()
        assertThat(result.notes).isNull()
    }

    @Test
    fun `llm blank with selected empty returns no-matches`() {
        every { searchUseCase.search(any<SearchInput>()) } returns SearchOutput(
            matches = listOf(match("ze-menu-001", "x", 0.9))
        )
        every { selector.select(any(), topK = 10, maxChunksPerDoc = 2) } returns emptyList()
        every { contextBuilder.build(emptyList(), 3_000) } returns BuiltContext("", emptyList(), 0, false)
        every { promptBuilder.build(any(), any(), any()) } returns PromptPayload("S", "U")
        every { chatPort.complete(any()) } returns ChatOutput("")
        every { askLocalization.noContext("pt-BR") } returns "PT-SEM-CONTEXTO"

        val result = sut().handle(AskInput(query = "q", topK = 10, filter = emptyMap(), lang = "pt-BR"))

        assertThat(result.answer).isEqualTo("PT-SEM-CONTEXTO")
        assertThat(result.usedK).isEqualTo(0)
        assertThat(result.citations).isEmpty()
        assertThat(result.notes).isEqualTo("no-matches")
    }

    private fun match(id: String, text: String, score: Double) =
        SearchMatch(documentId = DocumentId(id), chunk = TextChunk(text), score = score)

    private fun src(doc: String, title: String, idx: Int, text: String, score: Double) =
        ContextSource(documentId = doc, title = title, chunkIndex = idx, text = text, score = score)
}