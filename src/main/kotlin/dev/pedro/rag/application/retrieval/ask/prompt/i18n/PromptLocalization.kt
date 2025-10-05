package dev.pedro.rag.application.retrieval.ask.prompt.i18n

interface PromptLocalization {
    fun labels(lang: String?): PromptLabels
}
