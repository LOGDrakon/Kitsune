package com.kitsune.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Raw response from the public, unauthenticated `GET public/models` catalog endpoint. */
@Serializable
data class ModelListResponse(
    val data: List<ModelListEntryDto> = emptyList(),
    @SerialName("object") val objectType: String? = null
)

@Serializable
data class ModelListEntryDto(
    val id: String,
    @SerialName("object") val objectType: String? = null,
    val created: Long? = null,
    @SerialName("owned_by") val ownedBy: String? = null,
    @SerialName("model_info") val modelInfo: ModelInfoDto? = null
)

@Serializable
data class ModelInfoDto(
    val id: String? = null,
    @SerialName("max_input_tokens") val maxInputTokens: Long? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Long? = null,
    @SerialName("input_cost_per_token") val inputCostPerToken: Double? = null,
    @SerialName("output_cost_per_token") val outputCostPerToken: Double? = null
)
