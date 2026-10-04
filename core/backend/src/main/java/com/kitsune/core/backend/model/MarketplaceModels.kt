package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateListingRequest(
    val type: String,
    val title: String,
    val description: String = "",
    val maturityRating: String = "SFW",
    val genre: String = "",
    val tags: List<String> = emptyList(),
    val personaData: MarketplacePersonaData? = null,
    val universeData: MarketplaceUniverseData? = null,
    /** Set when [type] is `PRESET_PACK`. */
    val presetData: MarketplacePresetPackData? = null,
    val imageBase64List: List<String> = emptyList()
)

/** A shareable pack of story presets (listing type `PRESET_PACK`). Mode values are the app's enum
 * names; one this version does not know is ignored on import. */
@Serializable
data class MarketplacePresetPackData(
    val version: Int = 1,
    val presets: List<MarketplaceStoryPresetData>
)

@Serializable
data class MarketplaceStoryPresetData(
    val name: String,
    val description: String = "",
    val storyPace: String? = null,
    val tone: String? = null,
    val involvement: String? = null,
    val rhythm: String? = null,
    val universe: String? = null,
    val intensity: String? = null,
    val replyLength: String? = null,
    val narrationBalance: String? = null,
    val voice: String? = null,
    val directive: String = "",
    val writingStyle: String = "",
    val temperature: Double? = null
)

@Serializable
data class ListingSummary(
    val id: String,
    val creatorId: String,
    val creatorName: String?,
    val type: String,
    val title: String,
    val description: String,
    val maturityRating: String,
    val genre: String,
    val tags: List<String>,
    val previewImageUrl: String?,
    val downloadCount: Int,
    val averageRating: Float,
    val reviewCount: Int,
    val createdAt: String,
    val isFollowingCreator: Boolean = false
)

@Serializable
data class ListingDetail(
    val id: String,
    val creatorId: String,
    val creatorName: String?,
    val type: String,
    val title: String,
    val description: String,
    val maturityRating: String,
    val genre: String,
    val tags: List<String>,
    val previewImageUrl: String?,
    val imageUrls: List<String>,
    val downloadCount: Int,
    val averageRating: Float,
    val reviewCount: Int,
    val createdAt: String,
    val personaData: MarketplacePersonaData? = null,
    val universeData: MarketplaceUniverseData? = null,
    val presetData: MarketplacePresetPackData? = null,
    val isOwned: Boolean = false,
    /** The user's own rating (1-5), or null. */
    val myRating: Int? = null,
    val isFollowingCreator: Boolean = false
)

@Serializable
data class ListingDownload(
    val type: String,
    val personaData: MarketplacePersonaData? = null,
    val universeData: MarketplaceUniverseData? = null,
    val presetData: MarketplacePresetPackData? = null,
    val imageUrls: List<String>,
    val tags: List<String> = emptyList()
)

@Serializable
data class MarketplacePersonaData(
    val name: String,
    val shortDescription: String,
    val personality: String,
    val scenario: String,
    val firstMessage: String,
    val exampleDialogues: String,
    val age: Int,
    val maturityTags: List<String>,
    val visualSheetJson: String?,
    val avatarImageBase64: String? = null,
    val galleryImageBase64List: List<String> = emptyList(),
    /** Author-written story tones shipped with the persona (2026-08-24), encoded exactly like
     *  [visualSheetJson] and for the same reason: this module cannot see `core:data`'s enums. Decoded
     *  by `decodeToneCards`. Null for a persona with no tones, and for every listing published before
     *  they existed. */
    val toneCardsJson: String? = null,
    /** The persona's alternative starting points (2026-08-24), encoded like [visualSheetJson].
     *  Until now they reached neither the marketplace nor the export file, so a character published
     *  with three written openings arrived with none. Decoded by `decodeEntryScenes`. */
    val entryScenesJson: String? = null,
    /** Inner drives (2026-08-23). They were added to `PersonaEntity` but never to this payload, so a
     *  persona bought from the marketplace arrived without the fields that most shape how it plays. */
    val desire: String = "",
    val fear: String = "",
    val flaw: String = "",
    val moralLine: String = "",
    val secret: String = ""
)

@Serializable
data class MarketplaceUniverseData(
    val name: String,
    val description: String,
    val genre: String,
    val visualStyle: String?,
    val factions: List<MarketplaceFactionData> = emptyList(),
    val locations: List<MarketplaceLocationData> = emptyList(),
    val npcs: List<MarketplaceNpcData> = emptyList(),
    val personas: List<MarketplacePersonaData> = emptyList(),
    val avatarImageBase64: String? = null
)

@Serializable
data class MarketplaceFactionData(
    val name: String,
    val description: String,
    val type: String,
    val alignment: String?
)

@Serializable
data class MarketplaceLocationData(
    val name: String,
    val description: String,
    val type: String
)

@Serializable
data class MarketplaceNpcData(
    val name: String,
    val description: String,
    val personality: String,
    val role: String,
    val age: Int?,
    val physicalDescription: String? = null
)

@Serializable
data class ListingIdResponse(
    val listingId: String,
    /** True when the server put the listing in its review queue instead of publishing it — new
     *  creators' first listings, or listings its optional pre-screen flagged. */
    val inReview: Boolean = false
)

@Serializable
data class MarketplaceListResponse(
    val listings: List<ListingSummary>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int
)

@Serializable
/** 1 to 5 stars. No review text (PRINCIPLES.md §6). */
data class CreateReviewRequest(val rating: Int)

@Serializable
data class ReviewResponse(
    val id: String,
    val userId: String,
    val userName: String?,
    val rating: Int,
    val createdAt: String
)

@Serializable
data class ReviewsResponse(val reviews: List<ReviewResponse>)

@Serializable
data class ReportRequest(val reason: String, val description: String = "")

@Serializable
data class SetUsernameRequest(val username: String)

@Serializable
data class UsernameResponse(val username: String?, val available: Boolean = true)

@Serializable
data class UserProfileResponse(
    val userId: String,
    val username: String?,
    val banned: Boolean,
    val frozen: Boolean,
    val banReason: String? = null
)

/** "Mes abonnements" — one followed creator, enriched with their current published-listing count. */
@Serializable
data class FollowedCreator(
    val creatorId: String,
    val creatorName: String?,
    val listingCount: Int,
    val followedAt: String
)

@Serializable
data class FollowedCreatorsResponse(val creators: List<FollowedCreator>)

@Serializable
data class NewFollowedListingsCountResponse(val count: Int)

@Serializable
data class FollowStatusResponse(val following: Boolean)