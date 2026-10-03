package com.kitsune.core.backend

import com.kitsune.core.backend.model.*
import retrofit2.Response
import retrofit2.http.*

interface KitsuneApi {

    @GET("config/models")
    suspend fun getModelConfig(): Response<ModelConfigResponse>

    @POST("auth/register-anonymous")
    suspend fun registerAnonymous(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login-google")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<AuthResponse>

    @GET("credits/balance")
    suspend fun getBalance(): Response<BalanceResponse>

    @POST("credits/claim-free")
    suspend fun claimFreeCredits(): Response<ClaimFreeResponse>

    /** One-time-ever bonus (server-enforced) offered when the user runs out of credits mid-scene
     * during their first-ever chat's guided mini-arc — see `ChatViewModel`/`SecureStorage.
     * KEY_FIRST_CHAT_ID` for the "important moment" detection this is called from. */
    @POST("credits/claim-first-chat-bonus")
    suspend fun claimFirstChatBonus(): Response<ClaimFreeResponse>

    @GET("credits/history")
    suspend fun getHistory(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<HistoryResponse>

    @GET("ratelimit/status")
    suspend fun getRateLimitStatus(): Response<RateLimitResponse>

    @GET("purchases/catalog")
    suspend fun getSkuCatalog(): Response<SkuCatalogResponse>

    @POST("purchases/verify-iap")
    suspend fun verifyPurchase(@Body request: VerifyPurchaseRequest): Response<PurchaseVerifyResponse>

    @GET("cosmetics/catalog")
    suspend fun getCosmeticCatalog(): Response<CosmeticCatalogResponse>

    @GET("cosmetics/owned")
    suspend fun getOwnedCosmetics(): Response<OwnedCosmeticsResponse>

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

    @POST("marketplace/listings/{id}/translate")
    suspend fun translateListing(
        @Path("id") id: String,
        @Body request: TranslateListingRequest
    ): Response<TranslatedListingResult>

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

    /** Lightweight foreground ping used to derive session/engagement telemetry server-side. */
    @POST("v1/telemetry/session/heartbeat")
    suspend fun sessionHeartbeat(): Response<Unit>

    /** Body is hybrid RSA/AES-encrypted client-side (`BugReportCrypto`, core:security) before this
     * call — the backend only ever sees ciphertext, never the redacted report in the clear. */
    @POST("bugreports")
    suspend fun submitBugReport(@Body request: SubmitBugReportRequest): Response<SubmitBugReportResponse>

    // Account transfer (QR pairing) — see core:transfer. /transfer/create requires the normal
    // auth-jwt (the old phone's existing session); every other endpoint is authorized by the
    // per-transfer upload/pull token in X-Transfer-Token instead, since the new phone has no
    // account session yet at that point.
    @POST("transfer/create")
    suspend fun createTransfer(): Response<CreateTransferResponse>

    @PUT("transfer/{id}/upload")
    suspend fun uploadTransferBlob(
        @Path("id") transferId: String,
        @Header("X-Transfer-Token") token: String,
        @Body request: UploadTransferRequest
    ): Response<Unit>

    @GET("transfer/{id}/download")
    suspend fun downloadTransferBlob(
        @Path("id") transferId: String,
        @Header("X-Transfer-Token") token: String
    ): Response<DownloadTransferResponse>

    @POST("transfer/{id}/claim")
    suspend fun claimTransfer(
        @Path("id") transferId: String,
        @Header("X-Transfer-Token") token: String
    ): Response<ClaimTransferResponse>

    @POST("transfer/{id}/complete")
    suspend fun completeTransfer(
        @Path("id") transferId: String,
        @Header("X-Transfer-Token") token: String
    ): Response<Unit>

    @GET("transfer/{id}/status")
    suspend fun getTransferStatus(
        @Path("id") transferId: String,
        @Header("X-Transfer-Token") token: String
    ): Response<TransferStatusResponse>
}
