package com.kitsune.core.models

import androidx.annotation.StringRes

/** Combinable with [ModelCostFilter] — both narrow the list simultaneously, independent of sort. */
enum class ModelContextFilter(@StringRes val labelRes: Int, val minTokens: Int?) {
    ANY(R.string.model_context_any, null),
    AT_LEAST_32K(R.string.model_context_32k, 32_000),
    AT_LEAST_128K(R.string.model_context_128k, 128_000),
    AT_LEAST_500K(R.string.model_context_500k, 500_000)
}