package dev.pedro.rag.config.retrieval

import dev.pedro.rag.application.chat.ports.LlmChatPort
import dev.pedro.rag.application.retrieval.ask.context.ContextBuilder
import dev.pedro.rag.application.retrieval.ask.context.DefaultContextBuilder
import dev.pedro.rag.application.retrieval.ask.i18n.AskLocalization
import dev.pedro.rag.application.retrieval.ask.i18n.DefaultAskLocalization
import dev.pedro.rag.application.retrieval.ask.parsing.citation.CitationMapper
import dev.pedro.rag.application.retrieval.ask.parsing.citation.DefaultCitationMapper
import dev.pedro.rag.application.retrieval.ask.parsing.output.DefaultOutputParser
import dev.pedro.rag.application.retrieval.ask.parsing.output.OutputParser
import dev.pedro.rag.application.retrieval.ask.prompt.DefaultPromptBuilder
import dev.pedro.rag.application.retrieval.ask.prompt.PromptBuilder
import dev.pedro.rag.application.retrieval.ask.prompt.i18n.DefaultPromptLocalization
import dev.pedro.rag.application.retrieval.ask.prompt.i18n.PromptLocalization
import dev.pedro.rag.application.retrieval.ask.selection.ContextSelector
import dev.pedro.rag.application.retrieval.ask.selection.RoundRobinPerDocSelector
import dev.pedro.rag.application.retrieval.ask.usecase.AskUseCase
import dev.pedro.rag.application.retrieval.ask.usecase.DefaultAskUseCase
import dev.pedro.rag.application.retrieval.ports.EmbedPort
import dev.pedro.rag.application.retrieval.search.usecase.SearchUseCase
import dev.pedro.rag.infra.retrieval.metrics.MetricsAskUseCase
import dev.pedro.rag.infra.retrieval.metrics.RetrievalMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class RetrievalAskConfig {
    @Bean fun contextSelector(): ContextSelector = RoundRobinPerDocSelector()

    @Bean fun contextBuilder(): ContextBuilder = DefaultContextBuilder()

    @Bean fun promptLocalization(messageSource: MessageSource): PromptLocalization = DefaultPromptLocalization(messageSource)

    @Bean fun askLocalization(messageSource: MessageSource): AskLocalization = DefaultAskLocalization(messageSource)

    @Bean
    fun promptBuilder(
        localization: PromptLocalization,
        props: RetrievalAskProperties,
    ): PromptBuilder =
        DefaultPromptBuilder(
            localization = localization,
            requireCitations = props.prompt.requireCitations,
            requireAdmitUnknown = props.prompt.requireAdmitUnknown,
            autoDetectLang = props.language.autoDetect,
        )

    @Bean fun outputParser(): OutputParser = DefaultOutputParser(allowNsFromAnswer = true)

    @Bean fun citationMapper(): CitationMapper = DefaultCitationMapper()

    @Bean("askUseCaseCore")
    fun askUseCaseCore(
        searchUseCase: SearchUseCase,
        selector: ContextSelector,
        contextBuilder: ContextBuilder,
        promptBuilder: PromptBuilder,
        chatPort: LlmChatPort,
        outputParser: OutputParser,
        citationMapper: CitationMapper,
        askLocalization: AskLocalization,
        props: RetrievalAskProperties,
    ): AskUseCase =
        DefaultAskUseCase(
            searchUseCase = searchUseCase,
            selector = selector,
            contextBuilder = contextBuilder,
            promptBuilder = promptBuilder,
            chatPort = chatPort,
            outputParser = outputParser,
            citationMapper = citationMapper,
            askLocalization = askLocalization,
            poolTopK = props.poolK,
            maxChunksPerDoc = props.maxChunksPerDoc,
            budgetChars = props.contextBudgetChars,
        )

    @Bean
    @Primary
    fun askUseCase(
        @Qualifier("askUseCaseCore") core: AskUseCase,
        metrics: RetrievalMetrics,
        embedPort: EmbedPort,
    ): AskUseCase =
        MetricsAskUseCase(
            delegate = core,
            metrics = metrics,
            embedPort = embedPort,
        )
}
