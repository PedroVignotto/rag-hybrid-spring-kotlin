package dev.pedro.rag.application.retrieval.ask.usecase

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.application.retrieval.ask.context.BuiltContext
import dev.pedro.rag.application.retrieval.ask.context.ContextBuilder
import dev.pedro.rag.application.retrieval.ask.context.ContextSource
import dev.pedro.rag.application.retrieval.ask.dto.AskInput
import dev.pedro.rag.application.retrieval.ask.dto.AskOutput
import dev.pedro.rag.application.retrieval.ask.dto.Citation
import dev.pedro.rag.application.retrieval.ask.i18n.AskLocalization
import dev.pedro.rag.application.retrieval.ask.mappers.toContextSources
import dev.pedro.rag.application.retrieval.ask.parsing.citation.CitationMapper
import dev.pedro.rag.application.retrieval.ask.parsing.output.OutputParser
import dev.pedro.rag.application.retrieval.ask.prompt.PromptBuilder
import dev.pedro.rag.application.retrieval.ask.selection.ContextSelector
import dev.pedro.rag.application.retrieval.search.dto.SearchInput
import dev.pedro.rag.application.retrieval.search.usecase.SearchUseCase
import dev.pedro.rag.domain.chat.ChatInput
import dev.pedro.rag.domain.chat.ChatMessage
import dev.pedro.rag.domain.chat.ChatRole
import kotlin.math.min

internal class DefaultAskUseCase(
    private val searchUseCase: SearchUseCase,
    private val selector: ContextSelector,
    private val contextBuilder: ContextBuilder,
    private val promptBuilder: PromptBuilder,
    private val chatPort: LlmChatPort,
    private val outputParser: OutputParser,
    private val citationMapper: CitationMapper,
    private val askLocalization: AskLocalization,
    private val poolTopK: Int = 12,
    private val maxChunksPerDoc: Int = 2,
    private val budgetChars: Int = 3_000,
) : AskUseCase {

    override fun handle(input: AskInput): AskOutput {
        val pool = fetchPool(input)
        if (pool.isEmpty()) return noMatches(input.lang)
        val selected = selectSources(pool, input.topK)
        val built = buildContext(selected)
        val raw = completeWithLlm(built, input)
        if (raw.isNullOrBlank()) return extractiveFallback(selected, input.lang)
        return parseAndMap(raw, built)
    }

    private fun fetchPool(input: AskInput): List<ContextSource> {
        val searchInput = SearchInput(
            queryText = input.query,
            topK = poolTopK,
            filter = input.filter
        )
        return runCatching { searchUseCase.search(searchInput).toContextSources() }.getOrElse { emptyList() }
    }

    private fun selectSources(pool: List<ContextSource>, desiredTopK: Int): List<ContextSource> =
        selector.select(
            sources = pool,
            topK = desiredTopK,
            maxChunksPerDoc = maxChunksPerDoc
        )

    private fun buildContext(selected: List<ContextSource>): BuiltContext =
        contextBuilder.build(
            sources = selected,
            budgetChars = budgetChars
        )

    private fun completeWithLlm(built: BuiltContext, ask: AskInput): String? {
        val prompt = promptBuilder.build(
            context = built,
            query = ask.query,
            lang = ask.lang
        )
        return runCatching {
            val llmInput = ChatInput(
                messages = listOf(
                    ChatMessage(ChatRole.SYSTEM, prompt.system),
                    ChatMessage(ChatRole.USER, prompt.user)
                )
            )
            chatPort.complete(llmInput).content
        }.getOrNull()
    }

    private fun parseAndMap(raw: String, built: BuiltContext): AskOutput {
        val parsed = outputParser.parse(raw)
        val citations = citationMapper.map(parsed.citationNs, built)
        val notes = if (citations.isEmpty() && built.usedK > 0) "llm-no-citations" else null
        return AskOutput(
            answer = parsed.answer,
            citations = citations,
            usedK = built.usedK,
            notes = notes
        )
    }

    private fun extractiveFallback(selected: List<ContextSource>, lang: String?): AskOutput {
        if (selected.isEmpty()) return noMatches(lang)
        val take = min(2, selected.size)
        val excerpts = selected.take(take)
        val answer = buildString {
            excerpts.forEachIndexed { idx, src ->
                if (idx > 0) appendLine().appendLine()
                append("[${idx + 1}] ").append(src.text.trim())
            }
        }
        val citations = excerpts.map { src ->
            Citation(
                documentId = src.documentId,
                title = src.title,
                chunkIndex = src.chunkIndex
            )
        }
        return AskOutput(
            answer = answer,
            citations = citations,
            usedK = take,
            notes = "extractive-fallback"
        )
    }

    private fun noMatches(lang: String?): AskOutput =
        AskOutput(
            answer = askLocalization.noContext(lang),
            citations = emptyList(),
            usedK = 0,
            notes = "no-matches"
        )
}
