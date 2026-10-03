package com.kitsune.core.backend

import android.content.Context
import android.provider.Settings
import com.kitsune.core.backend.model.*
import com.kitsune.core.security.storage.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KitsuneBackendClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _authState = MutableStateFlow(AuthState.UNAUTHENTICATED)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _creditBalance = MutableStateFlow(0)
    val creditBalance: StateFlow<Int> = _creditBalance.asStateFlow()

    private val _isKitsunePlus = MutableStateFlow(false)
    val isKitsunePlus: StateFlow<Boolean> = _isKitsunePlus.asStateFlow()

    /** A daily login bonus that has been paid but not yet shown to the user, or null.
     *
     * Deliberately a [StateFlow] holding pending state rather than an event stream: the grant
     * almost always lands in the balance refresh that runs at login, *before* any screen capable
     * of displaying it has been composed. A `SharedFlow` event emitted at that moment would go to
     * zero subscribers and be dropped, and the user would silently never learn they got credits —
     * which is the entire point of the mechanic. Held until [acknowledgeDailyGrant] clears it.
     * There is no risk of showing a stale grant: this lives in a process-scoped singleton, so it
     * only ever holds a payout that happened during this run of the app. */
    private val _pendingDailyGrant = MutableStateFlow<Int?>(null)
    val pendingDailyGrant: StateFlow<Int?> = _pendingDailyGrant.asStateFlow()

    /** Called once the daily-bonus popup has been shown, so it isn't shown again. */
    fun acknowledgeDailyGrant() {
        _pendingDailyGrant.value = null
    }

    /** Ofudas the daily bonus can still ever pay this account; 0 once it is exhausted. */
    private val _dailyFreeCreditsRemaining = MutableStateFlow(0)
    val dailyFreeCreditsRemaining: StateFlow<Int> = _dailyFreeCreditsRemaining.asStateFlow()

    /** "NONE"/"PLUS"/"MAX". Only the store reads this, to mark the plan already owned — every
     * perk check uses [isKitsunePlus], since Kitsune Max is Kitsune+ with a bigger monthly Ofuda
     * grant and nothing else. */
    private val _subscriptionTier = MutableStateFlow("NONE")
    val subscriptionTier: StateFlow<String> = _subscriptionTier.asStateFlow()

    /** Set from [getUserProfile] — null means "not banned/frozen (or not checked yet)". Note:
     * BANNED no longer takes over the whole app (see MainActivity) — only FROZEN does. A banned
     * user keeps normal access (settings, existing chats) but every content-generation call is
     * rejected server-side (`checkAccountStatus`); this flow lets the UI show why. */
    private val _accountLockReason = MutableStateFlow<AccountLockReason?>(null)
    val accountLockReason: StateFlow<AccountLockReason?> = _accountLockReason.asStateFlow()

    /** Reason text for a ban, if any and if the admin provided one — set alongside
     * [accountLockReason] from [getUserProfile]. */
    private val _banReason = MutableStateFlow<String?>(null)
    val banReason: StateFlow<String?> = _banReason.asStateFlow()

    private val authMutex = Mutex()
    private val api: KitsuneApi

    init {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .addInterceptor(TokenRefreshInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        api = retrofit.create(KitsuneApi::class.java)
    }

    suspend fun registerAnonymous(): Result<AuthResponse> = runCatching {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val response = api.registerAnonymous(RegisterRequest(deviceId = deviceId))
        if (response.isSuccessful && response.body() != null) {
            val auth = response.body()!!
            saveTokens(auth.accessToken, auth.refreshToken)
            saveUserId(auth.userId)
            _authState.value = AuthState.AUTHENTICATED
            refreshBalance()
            auth
        } else {
            throw BackendException("Registration failed: ${response.code()}")
        }
    }

    suspend fun loginWithGoogle(googleIdToken: String, displayName: String? = null): Result<AuthResponse> = runCatching {
        val response = api.loginWithGoogle(GoogleLoginRequest(googleIdToken, displayName))
        if (response.isSuccessful && response.body() != null) {
            val auth = response.body()!!
            saveTokens(auth.accessToken, auth.refreshToken)
            saveUserId(auth.userId)
            _authState.value = AuthState.AUTHENTICATED
            refreshBalance()
            auth
        } else {
            throw BackendException("Google login failed: ${response.code()}")
        }
    }

    fun syncCreditBalance(balance: Int) {
        _creditBalance.value = balance
    }

    suspend fun refreshBalance(): Result<Int> = runCatching {
        val response = api.getBalance()
        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            _creditBalance.value = body.balance
            _hasClaimedFreeCredits.value = body.hasClaimedFreeCredits
            _isKitsunePlus.value = body.kitsunePlus
            _subscriptionTier.value = body.subscriptionTier
            _dailyFreeCreditsRemaining.value = body.dailyFreeCreditsRemaining
            if (body.dailyCreditsGranted > 0) _pendingDailyGrant.value = body.dailyCreditsGranted
            _firstCreationFree.value = body.firstCreationFree
            _firstImageFree.value = body.firstImageFree
            _imageCostCredits.value = body.imageCostCredits
            _imageHdCostCredits.value = body.imageHdCostCredits
            _personaGenCostCredits.value = body.personaGenCostCredits
            _universeGenCostCredits.value = body.universeGenCostCredits
            body.balance
        } else {
            throw BackendException("Failed to get balance: ${response.code()}")
        }
    }

    private val _hasClaimedFreeCredits = MutableStateFlow(false)
    val hasClaimedFreeCredits: StateFlow<Boolean> = _hasClaimedFreeCredits.asStateFlow()

    /** True if the user's NEXT quick persona/universe generation would be free (first-time
     * perk — see `ProxyRoutes.kt`'s `isFirstTimePerk`), refreshed alongside the credit balance.
     * Lets pre-generation cost dialogs show "Ce coup-ci c'est cadeau !" instead of a cost that
     * may not actually be charged. Purely informational — the server is still authoritative
     * about what's actually charged at generation time. */
    private val _firstCreationFree = MutableStateFlow(false)
    val firstCreationFree: StateFlow<Boolean> = _firstCreationFree.asStateFlow()

    /** Conservé pour compatibilité, mais le serveur renvoie désormais toujours `false` : la première
     * image n'est plus offerte (2026-08-20). */
    private val _firstImageFree = MutableStateFlow(false)
    val firstImageFree: StateFlow<Boolean> = _firstImageFree.asStateFlow()

    /** Prix d'une génération d'image en Ofudas, tel qu'annoncé par le serveur — l'écran de génération
     * le codait en dur, si bien qu'un changement de tarif serveur faisait afficher un prix faux aux
     * versions déjà installées. Rafraîchi avec le solde. */
    private val _imageCostCredits = MutableStateFlow(3)
    val imageCostCredits: StateFlow<Int> = _imageCostCredits.asStateFlow()

    /** Tarif du niveau HD, annoncé par le serveur. */
    private val _imageHdCostCredits = MutableStateFlow(8)
    val imageHdCostCredits: StateFlow<Int> = _imageHdCostCredits.asStateFlow()

    /** Prix **par proposition** d'une génération rapide de persona / d'univers, annoncé par le
     * serveur. Les écrans de création supposaient 1 Ofuda par proposition ; ils multiplient
     * désormais le nombre de propositions par ces tarifs. */
    private val _personaGenCostCredits = MutableStateFlow(3)
    val personaGenCostCredits: StateFlow<Int> = _personaGenCostCredits.asStateFlow()

    private val _universeGenCostCredits = MutableStateFlow(4)
    val universeGenCostCredits: StateFlow<Int> = _universeGenCostCredits.asStateFlow()

    suspend fun fetchModelConfig(): Result<ModelConfigResponse> = runCatching {
        val response = api.getModelConfig()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get model config: ${response.code()}")
        }
    }

    suspend fun downloadListing(id: String): Result<ListingDownload> = runCatching {
        val response = api.downloadListing(id)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to download listing: ${response.code()}")
        }
    }

    suspend fun getUserProfile(): Result<UserProfileResponse> = runCatching {
        val response = api.getUserProfile()
        if (response.isSuccessful && response.body() != null) {
            val profile = response.body()!!
            _accountLockReason.value = when {
                profile.banned -> AccountLockReason.BANNED
                profile.frozen -> AccountLockReason.FROZEN
                else -> null
            }
            _banReason.value = profile.banReason
            profile
        } else {
            throw BackendException("Failed to get user profile: ${response.code()}")
        }
    }

    suspend fun getProposals(): Result<List<ProposalResponse>> = runCatching {
        val response = api.getProposals()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get proposals: ${response.code()}")
        }
    }

    suspend fun createProposal(title: String, description: String): Result<String> = runCatching {
        val response = api.createProposal(CreateProposalRequest(title, description))
        if (response.isSuccessful && response.body() != null) {
            response.body()!!.id
        } else {
            throw BackendException("Failed to create proposal: ${response.code()}")
        }
    }

    suspend fun voteProposal(proposalId: String, voteType: String): Result<Unit> = runCatching {
        val response = api.voteProposal(proposalId, VoteProposalRequest(voteType))
        if (!response.isSuccessful) {
            throw BackendException("Failed to vote on proposal: ${response.code()}")
        }
    }

    suspend fun submitBugReport(encryptedKey: String, iv: String, ciphertext: String): Result<Unit> = runCatching {
        val response = api.submitBugReport(SubmitBugReportRequest(encryptedKey, iv, ciphertext))
        if (!response.isSuccessful) {
            throw BackendException("Failed to submit bug report: ${response.code()}")
        }
    }

    suspend fun setUsername(username: String): Result<UsernameResponse> = runCatching {
        val response = api.setUsername(SetUsernameRequest(username))
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to set username: ${response.code()}")
        }
    }

    suspend fun checkUsername(username: String): Result<UsernameResponse> = runCatching {
        val response = api.checkUsername(username)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to check username: ${response.code()}")
        }
    }

    /** Deletes the account server-side (credits, purchases, marketplace listings, everything) —
     * irreversible. Does not touch local state; the caller wipes local data and logs out only
     * after this succeeds, to never lose local data on a network failure with nothing deleted. */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val response = api.deleteAccount()
        if (!response.isSuccessful || response.body()?.success != true) {
            throw BackendException("Failed to delete account: ${response.code()}")
        }
    }

    suspend fun claimFreeCredits(): Result<ClaimFreeResponse> = runCatching {
        val response = api.claimFreeCredits()
        if (response.isSuccessful && response.body() != null) {
            val result = response.body()!!
            if (result.success) {
                _creditBalance.value = result.newBalance
                _hasClaimedFreeCredits.value = true
            }
            result
        } else {
            throw BackendException("Failed to claim free credits: ${response.code()}")
        }
    }

    /** Returns true if the bonus was actually granted (false if already claimed previously —
     * the backend enforces this once-ever regardless of how many times this is called). */
    suspend fun claimFirstChatBonus(): Result<Boolean> = runCatching {
        val response = api.claimFirstChatBonus()
        if (response.isSuccessful && response.body() != null) {
            val result = response.body()!!
            if (result.success) {
                _creditBalance.value = result.newBalance
            }
            result.success
        } else {
            throw BackendException("Failed to claim first-chat bonus: ${response.code()}")
        }
    }

    suspend fun getRateLimitStatus(): Result<RateLimitResponse> = runCatching {
        val response = api.getRateLimitStatus()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get rate limit: ${response.code()}")
        }
    }

    suspend fun verifyPurchase(sku: String, purchaseToken: String, orderId: String): Result<PurchaseVerifyResponse> = runCatching {
        val response = api.verifyPurchase(VerifyPurchaseRequest(sku, purchaseToken, orderId))
        if (response.isSuccessful && response.body() != null) {
            val result = response.body()!!
            if (result.success) {
                _creditBalance.value = result.newBalance
            }
            result
        } else {
            throw BackendException("Purchase verification failed: ${response.code()}")
        }
    }

    suspend fun getSkuCatalog(): Result<SkuCatalogResponse> = runCatching {
        val response = api.getSkuCatalog()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get SKU catalog: ${response.code()}")
        }
    }

    suspend fun getCosmeticCatalog(): Result<CosmeticCatalogResponse> = runCatching {
        val response = api.getCosmeticCatalog()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get cosmetic catalog: ${response.code()}")
        }
    }

    suspend fun getOwnedCosmetics(): Result<OwnedCosmeticsResponse> = runCatching {
        val response = api.getOwnedCosmetics()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get owned cosmetics: ${response.code()}")
        }
    }

    suspend fun getListings(
        type: String? = null,
        genre: String? = null,
        maturity: String? = null,
        sort: String? = null,
        page: Int = 0,
        pageSize: Int = 20,
        includeMature: Boolean = false,
        search: String? = null,
        creatorId: String? = null
    ): Result<MarketplaceListResponse> = runCatching {
        val response = api.getListings(type, genre, maturity, sort, page, pageSize, includeMature, search, creatorId)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get listings: ${response.code()}")
        }
    }

    suspend fun getListing(id: String): Result<ListingDetail> = runCatching {
        val response = api.getListing(id)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get listing: ${response.code()}")
        }
    }

    suspend fun createListing(request: CreateListingRequest): Result<ListingIdResponse> = runCatching {
        val response = api.createListing(request)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            // Unlike every other call site in this file, this one surfaces the server's actual
            // error text (e.g. "username required", moderation rejection, account banned) rather
            // than a generic "failed: <code>" — publishToMarketplace shows this message verbatim
            // to the user (PersonaDetailScreen/UniverseDetailScreen), so a bare HTTP code isn't
            // actionable for them the way it is for other, more internal-facing calls.
            val serverMessage = runCatching {
                response.errorBody()?.string()?.let { json.decodeFromString<ErrorResponse>(it).error }
            }.getOrNull()?.takeIf { it.isNotBlank() }
            throw BackendException(serverMessage ?: "Failed to create listing: ${response.code()}")
        }
    }

    suspend fun getMyListings(): Result<MarketplaceListResponse> = runCatching {
        val response = api.getMyListings()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get my listings: ${response.code()}")
        }
    }

    suspend fun deleteListing(id: String): Result<Unit> = runCatching {
        val response = api.deleteListing(id)
        if (!response.isSuccessful) {
            throw BackendException("Failed to delete listing: ${response.code()}")
        }
    }

    suspend fun followCreator(creatorId: String): Result<FollowStatusResponse> = runCatching {
        val response = api.followCreator(creatorId)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to follow creator: ${response.code()}")
        }
    }

    suspend fun unfollowCreator(creatorId: String): Result<FollowStatusResponse> = runCatching {
        val response = api.unfollowCreator(creatorId)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to unfollow creator: ${response.code()}")
        }
    }

    suspend fun getFollowedCreators(): Result<FollowedCreatorsResponse> = runCatching {
        val response = api.getFollowedCreators()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get followed creators: ${response.code()}")
        }
    }

    suspend fun getNewFollowedListingsCount(): Result<Int> = runCatching {
        val response = api.getNewFollowedListingsCount()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!.count
        } else {
            throw BackendException("Failed to get new follows count: ${response.code()}")
        }
    }

    suspend fun markFollowsSeen(): Result<Unit> = runCatching {
        val response = api.markFollowsSeen()
        if (!response.isSuccessful) {
            throw BackendException("Failed to mark follows seen: ${response.code()}")
        }
    }

    /** Aggregate creator profile (badges + progress + stats + follower count). Call with
     * [getUserId] for the caller's own "Mes badges" screen, or with a tapped creator's id for the
     * public creator profile screen. */
    suspend fun getCreatorProfile(creatorId: String): Result<CreatorProfile> = runCatching {
        val response = api.getCreatorProfile(creatorId)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get creator profile: ${response.code()}")
        }
    }

    suspend fun addReview(id: String, request: CreateReviewRequest): Result<Unit> = runCatching {
        val response = api.addReview(id, request)
        if (!response.isSuccessful) {
            throw BackendException("Failed to add review: ${response.code()}")
        }
    }

    suspend fun reportListing(id: String, request: ReportRequest): Result<Unit> = runCatching {
        val response = api.reportListing(id, request)
        if (!response.isSuccessful) {
            throw BackendException("Failed to report listing: ${response.code()}")
        }
    }

    /** Server-side cached translation of a marketplace listing's persona sheet — called from
     * `MarketplaceViewModel.translateListing` (listing detail screen, before download).
     * [TranslatedListingResult.cached] is true when this listing+language pair was already
     * translated by a previous request from any user. */
    suspend fun translateListing(id: String, targetLanguage: String): Result<TranslatedListingResult> = runCatching {
        val response = api.translateListing(id, TranslateListingRequest(targetLanguage))
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to translate listing: ${response.code()}")
        }
    }

    suspend fun getActiveAnnouncements(): Result<List<AnnouncementResponse>> = runCatching {
        val response = api.getActiveAnnouncements()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get announcements: ${response.code()}")
        }
    }

    suspend fun dismissAnnouncement(id: String): Result<DismissAnnouncementResponse> = runCatching {
        val response = api.dismissAnnouncement(id)
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            throw BackendException("Failed to dismiss announcement: ${response.code()}")
        }
        // Reflects an announcement's reward-credit grant immediately in the shared balance StateFlow
        // (chat top bar, settings, store, home) without an extra round trip to refreshBalance().
        if (body.newBalance != null) {
            syncCreditBalance(body.newBalance)
        }
        body
    }

    suspend fun getUserMessages(): Result<List<UserMessageResponse>> = runCatching {
        val response = api.getUserMessages()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get messages: ${response.code()}")
        }
    }

    suspend fun markUserMessageRead(id: String): Result<Unit> = runCatching {
        val response = api.markUserMessageRead(id)
        if (!response.isSuccessful) {
            throw BackendException("Failed to mark message read: ${response.code()}")
        }
    }

    /** Fire-and-forget: telemetry only, must never surface an error to the user. */
    suspend fun sendSessionHeartbeat() {
        if (!isAuthenticated()) return
        runCatching { api.sessionHeartbeat() }
    }

    // === Account transfer (QR pairing) — see core:transfer ===

    suspend fun createTransfer(): Result<CreateTransferResponse> = runCatching {
        val response = api.createTransfer()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to create transfer: ${response.code()}")
        }
    }

    suspend fun uploadTransferBlob(transferId: String, uploadToken: String, blobBase64: String): Result<Unit> = runCatching {
        val response = api.uploadTransferBlob(transferId, uploadToken, UploadTransferRequest(blobBase64))
        if (!response.isSuccessful) throw BackendException("Failed to upload transfer blob: ${response.code()}")
    }

    suspend fun downloadTransferBlob(transferId: String, pullToken: String): Result<DownloadTransferResponse> = runCatching {
        val response = api.downloadTransferBlob(transferId, pullToken)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to download transfer blob: ${response.code()}")
        }
    }

    suspend fun claimTransfer(transferId: String, pullToken: String): Result<ClaimTransferResponse> = runCatching {
        val response = api.claimTransfer(transferId, pullToken)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to claim transfer: ${response.code()}")
        }
    }

    suspend fun completeTransfer(transferId: String, pullToken: String): Result<Unit> = runCatching {
        val response = api.completeTransfer(transferId, pullToken)
        if (!response.isSuccessful) throw BackendException("Failed to complete transfer: ${response.code()}")
    }

    suspend fun getTransferStatus(transferId: String, token: String): Result<TransferStatusResponse> = runCatching {
        val response = api.getTransferStatus(transferId, token)
        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw BackendException("Failed to get transfer status: ${response.code()}")
        }
    }

    /** Installs a token pair obtained via [claimTransfer] — same account as before (the recovered
     * one), so unlike [registerAnonymous]/[loginWithGoogle] this never mints a new user. */
    suspend fun installTransferredSession(userId: String, accessToken: String, refreshToken: String) {
        saveTokens(accessToken, refreshToken)
        saveUserId(userId)
        _authState.value = AuthState.AUTHENTICATED
        refreshBalance()
    }

    suspend fun logout() {
        secureStorage.remove("access_token")
        secureStorage.remove("refresh_token")
        _authState.value = AuthState.UNAUTHENTICATED
        _creditBalance.value = 0
        _isKitsunePlus.value = false
        _subscriptionTier.value = "NONE"
        _pendingDailyGrant.value = null
        _dailyFreeCreditsRemaining.value = 0
    }

    fun isAuthenticated(): Boolean = _authState.value == AuthState.AUTHENTICATED

    fun getBackendUrl(): String = BuildConfig.BACKEND_BASE_URL + "/"

    fun getAccessToken(): String? = secureStorage.getString("access_token")

    fun getUserId(): String? = secureStorage.getString("user_id")

    private fun saveTokens(accessToken: String, refreshToken: String) {
        secureStorage.putString("access_token", accessToken)
        secureStorage.putString("refresh_token", refreshToken)
    }

    private fun saveUserId(userId: String) {
        secureStorage.putString("user_id", userId)
    }

    private fun getRefreshToken(): String? = secureStorage.getString("refresh_token")

    private suspend fun doTokenRefresh(): Boolean = authMutex.withLock {
        val refreshToken = getRefreshToken() ?: return@withLock false
        return@withLock try {
            val response = api.refreshToken(RefreshRequest(refreshToken))
            if (response.isSuccessful && response.body() != null) {
                val auth = response.body()!!
                saveTokens(auth.accessToken, auth.refreshToken)
                true
            } else {
                _authState.value = AuthState.UNAUTHENTICATED
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    inner class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
            getAccessToken()?.let { token ->
                request.addHeader("Authorization", "Bearer $token")
            }
            return chain.proceed(request.build())
        }
    }

    inner class TokenRefreshInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (response.code == 401) {
                response.close()
                try {
                    val refreshed = runBlocking { doTokenRefresh() }
                    if (refreshed) {
                        val newRequest = chain.request().newBuilder()
                        getAccessToken()?.let { token ->
                            newRequest.header("Authorization", "Bearer $token")
                        }
                        return chain.proceed(newRequest.build())
                    }
                } catch (e: Exception) {
                    // Silently fail â€” the original 401 response is already consumed
                }
            }
            return response
        }
    }

    enum class AuthState {
        UNAUTHENTICATED,
        AUTHENTICATED
    }
}

enum class AccountLockReason {
    BANNED,
    FROZEN
}

class BackendException(message: String) : Exception(message)
