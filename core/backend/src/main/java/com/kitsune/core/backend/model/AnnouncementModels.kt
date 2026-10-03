package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementResponse(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val dismissible: Boolean,
    val rewardCredits: Int? = null
)

@Serializable
data class DismissAnnouncementResponse(
    val success: Boolean,
    val rewardCredits: Int? = null,
    val newBalance: Int? = null
)
