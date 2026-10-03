package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

/** One-way message from the moderation team to this user (e.g. "your persona X was removed for
 * inappropriate content") — read-only, no reply. */
@Serializable
data class UserMessageResponse(
    val id: String,
    val subject: String,
    val body: String,
    val relatedListingId: String?,
    val createdAt: String,
    val readAt: String?
)
