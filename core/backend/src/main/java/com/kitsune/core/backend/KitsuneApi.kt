package com.kitsune.core.backend

import com.kitsune.core.backend.model.*
import retrofit2.Response
import retrofit2.http.*

interface KitsuneApi {

    @POST("auth/register-anonymous")
    suspend fun registerAnonymous(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<AuthResponse>

    @GET("marketplace/listings")
    suspend fun getListings(
        @Query("type") type: String? = null,
        @Query("genre") genre: String? = null,
        @Query("maturity") maturity: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 0,
        @Query("pageSize") pageSize: Int = 20,
        @Query("includeMature") includeMature: Boolean = false,
        @Query("search") search: String? = null,
        @Query("creatorId") creatorId: String? = null
    ): Response<MarketplaceListResponse>

    @GET("marketplace/listings/{id}")
    suspend fun getListing(@Path("id") id: String): Response<ListingDetail>

    @POST("marketplace/listings")
    suspend fun createListing(@Body request: CreateListingRequest): Response<ListingIdResponse>

    @DELETE("marketplace/listings/{id}")
    suspend fun deleteListing(@Path("id") id: String): Response<Unit>

    @POST("marketplace/listings/{id}/download")
    suspend fun downloadListing(@Path("id") id: String): Response<ListingDownload>

    @GET("user/profile")
    suspend fun getUserProfile(): Response<UserProfileResponse>

    @POST("user/username")
    suspend fun setUsername(@Body request: SetUsernameRequest): Response<UsernameResponse>

    @GET("user/check-username")
    suspend fun checkUsername(@Query("username") username: String): Response<UsernameResponse>

    @DELETE("user/account")
    suspend fun deleteAccount(): Response<DeleteAccountResponse>

    @POST("marketplace/listings/{id}/reviews")
    suspend fun addReview(
        @Path("id") id: String,
        @Body request: CreateReviewRequest
    ): Response<Unit>

    @GET("marketplace/listings/{id}/reviews")
    suspend fun getReviews(@Path("id") id: String): Response<ReviewsResponse>

    @POST("marketplace/listings/{id}/report")
    suspend fun reportListing(
        @Path("id") id: String,
        @Body request: ReportRequest
    ): Response<Unit>

    @GET("marketplace/my-listings")
    suspend fun getMyListings(): Response<MarketplaceListResponse>

    @POST("marketplace/creators/{creatorId}/follow")
    suspend fun followCreator(@Path("creatorId") creatorId: String): Response<FollowStatusResponse>

    @DELETE("marketplace/creators/{creatorId}/follow")
    suspend fun unfollowCreator(@Path("creatorId") creatorId: String): Response<FollowStatusResponse>

    @GET("marketplace/follows")
    suspend fun getFollowedCreators(): Response<FollowedCreatorsResponse>

    @GET("marketplace/follows/new-count")
    suspend fun getNewFollowedListingsCount(): Response<NewFollowedListingsCountResponse>

    @POST("marketplace/follows/mark-seen")
    suspend fun markFollowsSeen(): Response<Unit>

    /** Aggregate creator profile (identity + stats + follower count + per-badge progress) — backs
     * both "Mes badges" (called with one's own id) and the public creator profile screen. */
    @GET("marketplace/creators/{userId}/profile")
    suspend fun getCreatorProfile(@Path("userId") userId: String): Response<CreatorProfile>

    @GET("announcements/active")
    suspend fun getActiveAnnouncements(): Response<List<AnnouncementResponse>>

    @POST("announcements/{id}/dismiss")
    suspend fun dismissAnnouncement(@Path("id") id: String): Response<DismissAnnouncementResponse>

    /** One-way moderation messages to this user (e.g. "your persona X was removed") — see
     * `UserMessagingService` (KitsuneBackend). */
    @GET("messages")
    suspend fun getUserMessages(): Response<List<UserMessageResponse>>

    @POST("messages/{id}/read")
    suspend fun markUserMessageRead(@Path("id") id: String): Response<Unit>

    @GET("proposals")
    suspend fun getProposals(): Response<List<ProposalResponse>>

    @POST("proposals")
    suspend fun createProposal(@Body request: CreateProposalRequest): Response<ProposalIdResponse>

    @POST("proposals/{id}/vote")
    suspend fun voteProposal(@Path("id") id: String, @Body request: VoteProposalRequest): Response<Unit>
}
