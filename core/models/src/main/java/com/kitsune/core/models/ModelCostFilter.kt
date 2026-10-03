package com.kitsune.core.models

import androidx.annotation.StringRes

/** Combinable with [ModelContextFilter] — both narrow the list simultaneously, independent of sort. */
enum class ModelCostFilter(@StringRes val labelRes: Int, val maxCostPerMillionTokens: Double?) {
    ANY(R.string.model_cost_any, null),
    UNDER_1(R.string.model_cost_under_1, 1.0),
    UNDER_5(R.string.model_cost_under_5, 5.0),
    UNDER_20(R.string.model_cost_under_20, 20.0)
}