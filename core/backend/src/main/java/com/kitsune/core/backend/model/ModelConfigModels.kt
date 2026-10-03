package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelConfigResponse(
    val chat: String,
    val chatPro: String = "llama-4-maverick",
    val summary: String,
    val lore: String,
    val quickGeneration: String,
    val visualSheet: String,
    val imageDescription: String,
    val imageGeneration: String,
    val imageGenerationFallback: String = imageGeneration,
    val embedding: String,
    val translation: String = "qwen3.5-9b",
    val inspiration: String = "qwen3.5-9b",
    val maxContextTokens: Int? = null,
    val rawWindowSize: Int = 40,
    val rawWindowSizePro: Int = 60,
    val loreEntries: Int = 12,
    val loreEntriesPro: Int = 20,
    /** Server kill switch for the decoder penalties (2026-08-23). Defaults to on so a backend that
     *  predates the flag keeps the feature enabled. */
    val samplingEnabled: Boolean = true
)