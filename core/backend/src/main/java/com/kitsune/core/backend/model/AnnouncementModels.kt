package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementResponse(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val dismissible: Boolean
)

@Serializable
data class DismissAnnouncementResponse(val success: Boolean)
