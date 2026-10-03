package com.kitsune.core.models

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kitsune.core.network.catalog.ModelInfo
import java.util.Locale

@Composable
internal fun modelSubtitle(model: ModelInfo): String {
    val parts = mutableListOf<String>()
    parts.add(model.provider)
    model.contextWindowTokens?.let { tokens ->
        if (tokens >= 1_000) {
            parts.add(stringResource(R.string.model_tokens_k, tokens / 1_000))
        } else {
            parts.add(stringResource(R.string.model_tokens_count, tokens))
        }
    }
    if (model.hasPricing) {
        val input = String.format(Locale.US, "%.2f", model.inputCostPerMillionTokens)
        val output = String.format(Locale.US, "%.2f", model.outputCostPerMillionTokens)
        parts.add(stringResource(R.string.model_cost_format, input, output))
    }
    return parts.joinToString(" · ")
}