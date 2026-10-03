package com.kitsune.core.network.catalog

data class EstimatedCost(val inputUsd: Double, val outputUsd: Double) {
    val totalUsd: Double get() = inputUsd + outputUsd
}

/** Displayed to the user before sending a request (FEATURES.md section 1: "estimation de coût"). */
object CostEstimator {

    fun estimate(model: ModelInfo, estimatedInputTokens: Int, estimatedOutputTokens: Int): EstimatedCost? {
        if (!model.hasPricing) return null
        val inputUsd = model.inputCostPerMillionTokens!! * estimatedInputTokens / 1_000_000.0
        val outputUsd = model.outputCostPerMillionTokens!! * estimatedOutputTokens / 1_000_000.0
        return EstimatedCost(inputUsd, outputUsd)
    }
}
