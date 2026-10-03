package com.kitsune.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ModerationClassifyRequest(
    val text: String,
    val category: String
)

@Serializable
data class ModerationClassifyResponse(
    val allowed: Boolean
)
