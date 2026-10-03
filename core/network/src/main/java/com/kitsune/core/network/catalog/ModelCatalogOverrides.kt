package com.kitsune.core.network.catalog

/**
 * OpenRouter's catalog reports context size and pricing, but no speed or quality signal. This is a
 * small, manually-maintained overlay of hints for well-known flagship/lightweight models —
 * indicative only, not sourced from the API. Unlisted models simply fall back to
 * [SpeedTier.UNKNOWN] / [QualityTier.UNKNOWN] rather than a guess.
 *
 * Keyed by OpenRouter's full `vendor/slug` id (matching [ModelInfo.id]/[BackendCatalogModelDto.modelId],
 * which is no longer a separate bare id now that there's only one upstream) — not the bare model name
 * the five-direct-providers era used, which would now collide across vendors (e.g. two different
 * `gpt-4o`-named models from different resellers).
 */
object ModelCatalogOverrides {

    private val speedHints: Map<String, SpeedTier> = mapOf(
        "openai/gpt-4.1" to SpeedTier.MEDIUM,
        "openai/gpt-4o" to SpeedTier.FAST,
        "openai/gpt-5.1-chat" to SpeedTier.MEDIUM,
        "mistralai/mistral-medium-3.1" to SpeedTier.FAST,
        "google/gemini-3.1-flash-image-preview" to SpeedTier.FAST
    )

    private val qualityHints: Map<String, QualityTier> = mapOf(
        "openai/gpt-4.1" to QualityTier.HIGH,
        "openai/gpt-4o" to QualityTier.STANDARD,
        "openai/gpt-5.1-chat" to QualityTier.PREMIUM,
        "mistralai/mistral-medium-3.1" to QualityTier.STANDARD
    )

    fun speedFor(modelId: String): SpeedTier = speedHints[modelId] ?: SpeedTier.UNKNOWN

    fun qualityFor(modelId: String): QualityTier = qualityHints[modelId] ?: QualityTier.UNKNOWN
}
