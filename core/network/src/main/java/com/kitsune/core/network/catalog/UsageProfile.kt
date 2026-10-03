package com.kitsune.core.network.catalog

/** Preconfigured profiles applied automatically depending on the task at hand (FEATURES.md section 1). */
enum class UsageProfile {
    FAST,
    ECONOMICAL,
    QUALITY
}

/**
 * Picks a model for [profile] out of the live catalog, for use as a chat completion. Cost-based
 * ranking (ECONOMICAL/QUALITY) uses real pricing from OpenRouter's catalog; FAST falls back to the
 * smallest context window as a rough proxy when no [ModelCatalogOverrides] speed hint is
 * available, since the API itself reports no latency data.
 *
 * Non-chat-capable models (see [ModelInfo.isChatCapable]) are excluded up front — otherwise a
 * dirt-cheap embeddings model would win ECONOMICAL every time and fail outright when used for a
 * chat completion (see IDEAS.md — this was a real reported bug).
 */
fun List<ModelInfo>.pickForProfile(profile: UsageProfile): ModelInfo? {
    val candidates = filter { it.isChatCapable }
    if (candidates.isEmpty()) return null

    return when (profile) {
        UsageProfile.ECONOMICAL -> candidates.filter { it.hasPricing }
            .minByOrNull { it.inputCostPerMillionTokens!! + it.outputCostPerMillionTokens!! }
            ?: candidates.first()

        UsageProfile.QUALITY -> candidates.withQuality(QualityTier.PREMIUM).firstOrNull()
            ?: candidates.filter { it.hasPricing }.maxByOrNull { it.inputCostPerMillionTokens!! + it.outputCostPerMillionTokens!! }
            ?: candidates.first()

        UsageProfile.FAST -> candidates.withSpeed(SpeedTier.FAST).firstOrNull()
            ?: candidates.minByOrNull { it.contextWindowTokens ?: Int.MAX_VALUE }
            ?: candidates.first()
    }
}
