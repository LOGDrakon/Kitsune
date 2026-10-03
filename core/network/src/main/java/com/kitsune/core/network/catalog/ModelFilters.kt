package com.kitsune.core.network.catalog

/** Filters backing the model catalog screen (see FEATURES.md section 1). */

fun List<ModelInfo>.withMinContext(minTokens: Int): List<ModelInfo> =
    filter { (it.contextWindowTokens ?: 0) >= minTokens }

fun List<ModelInfo>.withMaxCostPerMillionTokens(maxUsd: Double): List<ModelInfo> =
    filter { it.hasPricing && (it.inputCostPerMillionTokens!! + it.outputCostPerMillionTokens!!) <= maxUsd }

fun List<ModelInfo>.withSpeed(tier: SpeedTier): List<ModelInfo> = filter { it.speedTier == tier }

fun List<ModelInfo>.withQuality(tier: QualityTier): List<ModelInfo> = filter { it.qualityTier == tier }
