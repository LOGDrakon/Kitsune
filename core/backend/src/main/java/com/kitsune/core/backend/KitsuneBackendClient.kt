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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for a Kitsune **marketplace server** — the only server the app talks to, and an optional
 * one. AI calls never come here: they go straight to the user's own provider (`core:network`).
 *
 * What a marketplace server provides: sharing and downloading personas/universes, reviews,
 * reports, following creators, idea proposals and votes, announcements, and one-way moderation
 * messages. It never sees a conversation.
 *
 * ## Optional, and pointed wherever the user wants
 *
 * [isEnabled] switches the whole thing off; the app then makes no request to any server at all.
 * [serverUrl] defaults to the official server but can point at anyone's instance of Kitsune-Server.
 * Changing it drops the session, since an account belongs to one server.
 *
 * ## Registration is lazy
 *
 * Nothing is sent until a marketplace feature is actually used ([ensureRegistered]), so someone who
 * never opens Discover never contacts any server. The anonymous registration sends the device's
 * `ANDROID_ID` — Android scopes it to the APK's signing key, so it is stable across reinstalls of the
 * same build, which is what lets a server operator ban an abusive device rather than just an
 * account that can be recreated in one tap.
 */
@Singleton
class KitsuneBackendClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _authState = MutableStateFlow(
        if (secureStorage.getString(KEY_ACCESS_TOKEN) != null) AuthState.AUTHENTICATED else AuthState.UNAUTHENTICATED
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _enabled = MutableStateFlow(secureStorage.getInt(KEY_ENABLED, 1) == 1)
    /** Whether marketplace features are on. When off, every call fails fast with
     * [MarketplaceDisabledException] and nothing touches the network. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _serverUrl = MutableStateFlow(storedServerUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    /** Set from [getUserProfile] — null means "not banned/frozen (or not checked yet)". A ban only
     * affects marketplace features: everything else in the app is local and keeps working. */
    private val _accountLockReason = MutableStateFlow<AccountLockReason?>(null)
    val accountLockReason: StateFlow<AccountLockReason?> = _accountLockReason.asStateFlow()

    private val _banReason = MutableStateFlow<String?>(null)
    val banReason: StateFlow<String?> = _banReason.asStateFlow()

    private val authMutex = Mutex()

    @Volatile private var apiFor: Pair<String, KitsuneApi>? = null

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .addInterceptor(TokenRefreshInterceptor())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api: KitsuneApi
        get() {
            val url = _serverUrl.value
            apiFor?.takeIf { it.first == url }?.let { return it.second }
            val created = Retrofit.Builder()
                .baseUrl("$url/")
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(KitsuneApi::class.java)
            apiFor = url to created
            return created
        }

    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(enabled: Boolean) {
        secureStorage.putInt(KEY_ENABLED, if (enabled) 1 else 0)
        _enabled.value = enabled
    }

    /** Points the app at another marketplace server. Returns false (and changes nothing) for a URL
     * that is not a valid http(s) address. Drops the current session: accounts are per server. */
    fun setServerUrl(url: String): Boolean {
        val normalized = normalizeUrl(url) ?: return false
        if (normalized == _serverUrl.value) return true
        secureStorage.putString(KEY_SERVER_URL, normalized)
        _serverUrl.value = normalized
        clearSession()
        return true
    }

    fun resetServerUrl() {
        secureStorage.remove(KEY_SERVER_URL)
        _serverUrl.value = BuildConfig.BACKEND_BASE_URL
        clearSession()
    }

    /** Re-reads the settings — after a backup restore wrote them behind this client's back. */
    fun reloadSettings() {
        _enabled.value = secureStorage.getInt(KEY_ENABLED, 1) == 1
        _serverUrl.value = storedServerUrl()
    }

    fun isDefaultServer(): Boolean = _serverUrl.value == BuildConfig.BACKEND_BASE_URL

    /** Registers an anonymous account on the current server if there is none yet. Called by every
     * marketplace operation, so registration happens on first real use and never before. */
    suspend fun ensureRegistered() {
        if (!isEnabled()) throw MarketplaceDisabledException()
        if (isAuthenticated()) return
        authMutex.withLock {
            if (isAuthenticated()) return
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val response = api.registerAnonymous(RegisterRequest(deviceId = deviceId))
            val auth = response.body()
            if (!response.isSuccessful || auth == null) {
                throw BackendException(serverError(response) ?: "Inscription impossible sur le serveur (${response.code()})")
            }
            saveTokens(auth.accessToken, auth.refreshToken)
            secureStorage.putString(KEY_USER_ID, auth.userId)
            _authState.value = AuthState.AUTHENTICATED
        }
    }

    /** Runs [block] against the API after making sure an account exists, and unwraps the response:
     * a body on success, the server's own error text otherwise. */
    private suspend fun <T> call(what: String, block: suspend KitsuneApi.() -> retrofit2.Response<T>): T {
        ensureRegistered()
        val response = api.block()
        val body = response.body()
        if (response.isSuccessful && body != null) return body
        throw BackendException(serverError(response) ?: "$what (${response.code()})")
    }

    private suspend fun callNoBody(what: String, block: suspend KitsuneApi.() -> retrofit2.Response<*>) {
        ensureRegistered()
        val response = api.block()
        if (!response.isSuccessful) throw BackendException(serverError(response) ?: "$what (${response.code()})")
    }

    private fun serverError(response: retrofit2.Response<*>): String? = runCatching {
        response.errorBody()?.string()?.let { json.decodeFromString<ErrorResponse>(it).error }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    suspend fun downloadListing(id: String): Result<ListingDownload> = runCatching {
        call("Échec du téléchargement") { downloadListing(id) }
    }

    suspend fun getUserProfile(): Result<UserProfileResponse> = runCatching {
        val profile = call("Profil indisponible") { getUserProfile() }
        _accountLockReason.value = when {
            profile.banned -> AccountLockReason.BANNED
            profile.frozen -> AccountLockReason.FROZEN
            else -> null
        }
        _banReason.value = profile.banReason
        profile
    }

    suspend fun getProposals(): Result<List<ProposalResponse>> = runCatching {
        call("Propositions indisponibles") { getProposals() }
    }

    suspend fun createProposal(title: String, description: String): Result<String> = runCatching {
        call("Envoi de la proposition impossible") { createProposal(CreateProposalRequest(title, description)) }.id
    }

    suspend fun voteProposal(proposalId: String, voteType: String): Result<Unit> = runCatching {
        callNoBody("Vote impossible") { voteProposal(proposalId, VoteProposalRequest(voteType)) }
    }

    suspend fun setUsername(username: String): Result<UsernameResponse> = runCatching {
        call("Pseudo refusé") { setUsername(SetUsernameRequest(username)) }
    }

    suspend fun checkUsername(username: String): Result<UsernameResponse> = runCatching {
        call("Vérification du pseudo impossible") { checkUsername(username) }
    }

    /** Deletes the account on the current server (listings, reviews, follows) — irreversible. Local
     * data is untouched: it never lived on the server. */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val result = call("Suppression du compte impossible") { deleteAccount() }
        if (!result.success) throw BackendException("Suppression du compte impossible")
        clearSession()
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
        call("Marketplace indisponible") { getListings(type, genre, maturity, sort, page, pageSize, includeMature, search, creatorId) }
    }

    suspend fun getListing(id: String): Result<ListingDetail> = runCatching {
        call("Fiche indisponible") { getListing(id) }
    }

    /** Surfaces the server's own error text (e.g. "username required", a rejected listing, a ban):
     * the publish screens show it verbatim, and a bare HTTP code would not tell the user anything. */
    suspend fun createListing(request: CreateListingRequest): Result<ListingIdResponse> = runCatching {
        call("Publication impossible") { createListing(request) }
    }

    suspend fun getMyListings(): Result<MarketplaceListResponse> = runCatching {
        call("Vos publications sont indisponibles") { getMyListings() }
    }

    suspend fun deleteListing(id: String): Result<Unit> = runCatching {
        callNoBody("Suppression impossible") { deleteListing(id) }
    }

    suspend fun followCreator(creatorId: String): Result<FollowStatusResponse> = runCatching {
        call("Abonnement impossible") { followCreator(creatorId) }
    }

    suspend fun unfollowCreator(creatorId: String): Result<FollowStatusResponse> = runCatching {
        call("Désabonnement impossible") { unfollowCreator(creatorId) }
    }

    suspend fun getFollowedCreators(): Result<FollowedCreatorsResponse> = runCatching {
        call("Abonnements indisponibles") { getFollowedCreators() }
    }

    suspend fun getNewFollowedListingsCount(): Result<Int> = runCatching {
        call("Nouveautés indisponibles") { getNewFollowedListingsCount() }.count
    }

    suspend fun markFollowsSeen(): Result<Unit> = runCatching {
        callNoBody("Mise à jour impossible") { markFollowsSeen() }
    }

    /** A creator's public numbers. Call with [getUserId] for the user's own, or a tapped creator's id. */
    suspend fun getCreatorProfile(creatorId: String): Result<CreatorProfile> = runCatching {
        call("Profil du créateur indisponible") { getCreatorProfile(creatorId) }
    }

    suspend fun addReview(id: String, request: CreateReviewRequest): Result<Unit> = runCatching {
        callNoBody("Avis refusé") { addReview(id, request) }
    }

    suspend fun reportListing(id: String, request: ReportRequest): Result<Unit> = runCatching {
        callNoBody("Signalement impossible") { reportListing(id, request) }
    }

    suspend fun getActiveAnnouncements(): Result<List<AnnouncementResponse>> = runCatching {
        call("Annonces indisponibles") { getActiveAnnouncements() }
    }

    suspend fun dismissAnnouncement(id: String): Result<DismissAnnouncementResponse> = runCatching {
        call("Impossible de masquer l'annonce") { dismissAnnouncement(id) }
    }

    suspend fun getUserMessages(): Result<List<UserMessageResponse>> = runCatching {
        call("Messages indisponibles") { getUserMessages() }
    }

    suspend fun markUserMessageRead(id: String): Result<Unit> = runCatching {
        callNoBody("Mise à jour impossible") { markUserMessageRead(id) }
    }

    /** Forgets the session on this device (the account still exists on the server). */
    fun logout() = clearSession()

    fun isAuthenticated(): Boolean = _authState.value == AuthState.AUTHENTICATED

    fun getUserId(): String? = secureStorage.getString(KEY_USER_ID)

    private fun getAccessToken(): String? = secureStorage.getString(KEY_ACCESS_TOKEN)

    private fun getRefreshToken(): String? = secureStorage.getString(KEY_REFRESH_TOKEN)

    private fun saveTokens(accessToken: String, refreshToken: String) {
        secureStorage.putString(KEY_ACCESS_TOKEN, accessToken)
        secureStorage.putString(KEY_REFRESH_TOKEN, refreshToken)
    }

    private fun clearSession() {
        secureStorage.remove(KEY_ACCESS_TOKEN)
        secureStorage.remove(KEY_REFRESH_TOKEN)
        secureStorage.remove(KEY_USER_ID)
        _authState.value = AuthState.UNAUTHENTICATED
        _accountLockReason.value = null
        _banReason.value = null
    }

    private fun storedServerUrl(): String =
        secureStorage.getString(KEY_SERVER_URL)?.let(::normalizeUrl) ?: BuildConfig.BACKEND_BASE_URL

    private suspend fun doTokenRefresh(): Boolean = authMutex.withLock {
        val refreshToken = getRefreshToken() ?: return@withLock false
        return@withLock try {
            val response = api.refreshToken(RefreshRequest(refreshToken))
            val auth = response.body()
            if (response.isSuccessful && auth != null) {
                saveTokens(auth.accessToken, auth.refreshToken)
                true
            } else {
                // The refresh token is dead (expired, server reset, account deleted): forget the
                // session so the next call registers again instead of failing forever.
                clearSession()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    inner class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
            getAccessToken()?.let { token -> request.addHeader("Authorization", "Bearer $token") }
            return chain.proceed(request.build())
        }
    }

    inner class TokenRefreshInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (response.code == 401 && !chain.request().url.encodedPath.endsWith("auth/refresh")) {
                response.close()
                try {
                    val refreshed = runBlocking { doTokenRefresh() }
                    if (refreshed) {
                        val newRequest = chain.request().newBuilder()
                        getAccessToken()?.let { token -> newRequest.header("Authorization", "Bearer $token") }
                        return chain.proceed(newRequest.build())
                    }
                } catch (e: Exception) {
                    // Fall through: the caller gets a failed call, the next one re-registers.
                }
                return chain.proceed(chain.request().newBuilder().removeHeader("Authorization").build())
            }
            return response
        }
    }

    enum class AuthState {
        UNAUTHENTICATED,
        AUTHENTICATED
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ENABLED = SecureStorage.KEY_MARKETPLACE_ENABLED
        private const val KEY_SERVER_URL = SecureStorage.KEY_MARKETPLACE_SERVER_URL

        /** `https://host[:port][/path]` without a trailing slash, or null if not a usable URL. */
        fun normalizeUrl(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
            val parsed = withScheme.toHttpUrlOrNull() ?: return null
            if (parsed.host.isBlank()) return null
            return withScheme
        }
    }
}

enum class AccountLockReason {
    BANNED,
    FROZEN
}

class BackendException(message: String) : Exception(message)

/** Thrown by every marketplace call while the marketplace is switched off in Settings. */
class MarketplaceDisabledException : Exception("La marketplace est désactivée. Activez-la dans Réglages → Marketplace.")
