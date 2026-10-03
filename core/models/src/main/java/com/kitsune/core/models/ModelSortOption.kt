package com.kitsune.core.models

import androidx.annotation.StringRes

enum class ModelSortOption(@StringRes val labelRes: Int) {
    QUALITY(R.string.model_sort_quality),
    COST(R.string.model_sort_cost),
    CONTEXT_SIZE(R.string.model_sort_context),
    SPEED(R.string.model_sort_speed)
}