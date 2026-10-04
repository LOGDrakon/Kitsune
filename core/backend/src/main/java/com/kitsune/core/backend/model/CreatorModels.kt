package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

/** `GET /marketplace/creators/{userId}/profile` — a creator's plain numbers, for their public page and
 * for the user's own "Vos publications" card. Creator badges were removed on 2026-10-04 (gamification,
 * see PRINCIPLES.md §2). */
@Serializable
data class CreatorProfile(
    val creatorId: String,
    val creatorName: String?,
    val listingCount: Int,
    val totalDownloadCount: Int,
    val averageRating: Float,
    val ratingCount: Int = 0,
    val followerCount: Int
)
