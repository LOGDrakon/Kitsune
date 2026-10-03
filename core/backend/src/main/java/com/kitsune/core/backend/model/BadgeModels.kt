package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

/** Client-side mirror of KitsuneBackend's `BadgeType` enum (`BadgeService.kt`) — kept separate
 * (not shared code across the two Gradle projects) and deliberately NOT the wire type: on the wire,
 * [BadgeProgress.badgeType] stays a raw [String] so a server that ships a 9th badge type doesn't
 * fail JSON deserialization for older app versions, it just falls back to a generic/unknown display
 * (see `BadgeDisplay`, core:designsystem). Use [fromWireValue] at the UI layer only. */
enum class BadgeType {
    DOWNLOADS_10, DOWNLOADS_50, DOWNLOADS_100, DOWNLOADS_500,
    RATING_4PLUS, RATING_5_WITH_10_REVIEWS, PROLIFIC, PIONEER;

    companion object {
        fun fromWireValue(value: String): BadgeType? = entries.find { it.name == value }
    }
}

/** Progress toward one badge. For the two binary badges (RATING_5_WITH_10_REVIEWS, PIONEER) there's
 * no partial-progress metric the server exposes — [currentValue]/[targetValue] are 0f/1f (locked) or
 * 1f/1f (earned) rather than a fabricated bar. */
@Serializable
data class BadgeProgress(
    val badgeType: String,
    val awarded: Boolean,
    val awardedAt: String?,
    val currentValue: Float,
    val targetValue: Float
)

/** `GET /marketplace/creators/{userId}/profile` — used both for "Mes badges" (call with one's own
 * id, via [com.kitsune.core.backend.KitsuneBackendClient.getUserId]) and the public creator profile
 * screen (call with the tapped creator's id). */
@Serializable
data class CreatorProfile(
    val creatorId: String,
    val creatorName: String?,
    val listingCount: Int,
    val totalDownloadCount: Int,
    val averageRating: Float,
    val followerCount: Int,
    val pioneerRank: Int,
    val pioneerCutoff: Int,
    val badges: List<BadgeProgress>
)
