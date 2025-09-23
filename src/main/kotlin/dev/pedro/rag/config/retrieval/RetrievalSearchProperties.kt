package dev.pedro.rag.config.retrieval

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("retrieval.search")
data class RetrievalSearchProperties(
    @field:Min(1)
    @field:Max(100)
    val k: Int = 10,
    @field:Valid
    val vector: Vector = Vector(),
    @field:Valid
    val bm25: Bm25 = Bm25(),
    @field:Valid
    val fusion: Fusion = Fusion(),
    @field:Valid
    val dedup: Dedup = Dedup(),
    @field:Valid
    val mmr: Mmr = Mmr(),
) {
    @AssertTrue(message = "At least one search source must be enabled (vector or bm25).")
    fun atLeastOneSourceEnabled(): Boolean = vector.enabled || bm25.enabled

    data class Vector(
        val enabled: Boolean = true,
        @field:Min(1)
        @field:Max(500)
        val width: Int = 100,
    )

    data class Bm25(
        val enabled: Boolean = true,
        @field:Min(1)
        @field:Max(500)
        val width: Int = 100,
        @field:DecimalMin("0.0")
        val termFrequencySaturation: Double = 1.2,
        @field:DecimalMin("0.0")
        @field:DecimalMax("1.0")
        val lengthNormalization: Double = 0.75,
        val stopWordsEnabled: Boolean = false,
        val stopWords: Set<String> = emptySet(),
    )

    data class Fusion(
        @field:DecimalMin("0.0")
        @field:DecimalMax("1.0")
        val alpha: Double = 0.5,
    )

    data class Dedup(
        val strictEnabled: Boolean = true,
        @field:Valid
        val soft: Soft = Soft(),
    ) {
        data class Soft(
            val enabled: Boolean = true,
            @field:DecimalMin("0.0")
            @field:DecimalMax("1.0")
            val overlapThreshold: Double = 0.75,
        )
    }

    data class Mmr(
        val enabled: Boolean = true,
        @field:DecimalMin("0.0")
        @field:DecimalMax("1.0")
        val lambda: Double = 0.3,
    )
}
