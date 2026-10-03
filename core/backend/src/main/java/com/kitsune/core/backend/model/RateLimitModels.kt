package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class RateLimitResponse(
    val messagesRemaining: Int,
    val imagesRemaining: Int,
    val tier: String
)
