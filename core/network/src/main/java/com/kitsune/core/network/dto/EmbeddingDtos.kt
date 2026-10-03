package com.kitsune.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class EmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
data class EmbeddingResponse(
    val model: String,
    val data: List<EmbeddingData>,
    val usage: EmbeddingUsageDto? = null
)

@Serializable
data class EmbeddingData(
    val index: Int,
    val embedding: List<Float>
)

@Serializable
data class EmbeddingUsageDto(
    val prompt_tokens: Int = 0,
    val total_tokens: Int = 0
)