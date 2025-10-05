package dev.pedro.rag.application.retrieval.ask.prompt.i18n

data class PromptLabels(
    val systemHeader: String,
    val ruleUseOnlyContext: String,
    val ruleCiteAllWithN: String,
    val ruleAdmitUnknown: String,
    val ruleOutputFormat: String,
    val context: String,
    val referenceIndex: String,
    val question: String,
    val responseFormatIntro: String,
    val contextEmptyHint: String,
    val answerHere: String,
)
