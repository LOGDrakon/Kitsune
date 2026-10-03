package com.kitsune.core.models

import androidx.annotation.StringRes
import com.kitsune.core.network.catalog.ModelInfo

/** Which [ModelPickerDialog] is being shown for — determines which catalog entries are selectable. */
enum class ModelCapabilityFilter(@StringRes val titleRes: Int) {
    CHAT(R.string.model_capability_chat_title),
    IMAGE(R.string.model_capability_image_title);

    fun matches(model: ModelInfo): Boolean = when (this) {
        CHAT -> model.isChatCapable
        IMAGE -> model.isImageCapable
    }
}