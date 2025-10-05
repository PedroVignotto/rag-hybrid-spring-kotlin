package dev.pedro.rag.application.retrieval.ask.usecase

import dev.pedro.rag.application.chat.usecase.ChatUseCase
import dev.pedro.rag.application.retrieval.ask.context.CitationIndex
import dev.pedro.rag.application.retrieval.ask.context.ContextBuilder
import dev.pedro.rag.application.retrieval.ask.dto.AskInput
import dev.pedro.rag.application.retrieval.ask.dto.AskOutput
import dev.pedro.rag.application.retrieval.ask.dto.Citation
import dev.pedro.rag.application.retrieval.ask.prompt.PromptBuilder
import dev.pedro.rag.application.retrieval.search.usecase.SearchUseCase
import dev.pedro.rag.config.retrieval.RetrievalAskProperties

class DefaultAskUseCase(
    private val searchUseCase: SearchUseCase,
    private val contextBuilder: ContextBuilder,
    private val promptBuilder: PromptBuilder,
    private val chatUseCase: ChatUseCase,
    private val props: RetrievalAskProperties,
) : AskUseCase {
    override fun handle(input: AskInput): AskOutput {
        // TODO implementação:
        // 1) search (pool = max(input.topK, props.poolK))
        // 2) build context (budget, maxChunksPerDoc)
        // 3) build prompt (lang)
        // 4) chat (LLM)
        // 5) parse ANSWER/CITATIONS
        // 6) map citations via mapCitations(...)
        throw UnsupportedOperationException("Not implemented yet")
    }

    private fun mapCitations(
        answer: String,
        citationsSection: String?,
        index: List<CitationIndex>,
    ): List<Citation> {
        if (index.isEmpty()) return emptyList()

        val indexByN = index.associateBy { it.n }
        val ns =
            if (!citationsSection.isNullOrBlank()) {
                extractNsFromCitationsSection(citationsSection)
            } else {
                extractNsFromAnswer(answer)
            }

        val seen = HashSet<Int>()
        val orderedDistinctNs = ns.filter { seen.add(it) }

        return orderedDistinctNs.mapNotNull { n ->
            indexByN[n]?.let { ci ->
                Citation(documentId = ci.documentId, title = ci.title, chunkIndex = ci.chunkIndex)
            }
        }
    }

    private fun extractNsFromCitationsSection(text: String): List<Int> {
        val regex = Regex("""\[(\d+)]""")
        return regex.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .toList()
    }

    private fun extractNsFromAnswer(answer: String): List<Int> {
        val regex = Regex("""\[(\d+)]""")
        return regex.findAll(answer)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .toList()
    }
}
