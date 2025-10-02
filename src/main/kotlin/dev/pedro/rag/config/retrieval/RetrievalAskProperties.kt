package dev.pedro.rag.config.retrieval

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("retrieval.ask")
data class RetrievalAskProperties(
    @field:Min(500)
    @field:Max(20000)
    val contextBudgetChars: Int = 3000,
    @field:Min(1)
    @field:Max(100)
    val poolK: Int = 12,
    @field:Min(1)
    @field:Max(10)
    val maxChunksPerDoc: Int = 2,
    val language: Language = Language(),
    val prompt: Prompt = Prompt(),
) {
    data class Language(
        val autoDetect: Boolean = true,
    )

    data class Prompt(
        val requireCitations: Boolean = true,
        val requireAdmitUnknown: Boolean = true,
    )
}
