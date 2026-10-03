package com.kitsune.feature.marketplace

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.network.persona.TranslatePersonaUseCase
import com.kitsune.core.backend.model.*
import com.kitsune.core.data.local.entities.*
import com.kitsune.core.data.repository.*
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.storage.EncryptedImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    private val backendClient: KitsuneBackendClient,
    private val personaRepository: PersonaRepository,
    private val universeRepository: UniverseRepository,
    private val factionRepository: FactionRepository,
    private val locationRepository: LocationRepository,
    private val npcRepository: NpcRepository,
    private val personaImageRepository: PersonaImageRepository,
    private val toneCardRepository: ToneCardRepository,
    private val entrySceneRepository: EntrySceneRepository,
    private val universeImageRepository: UniverseImageRepository,
    private val encryptedImageStore: EncryptedImageStore,
    private val appLanguageManager: AppLanguageManager,
    private val translatePersonaUseCase: TranslatePersonaUseCase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<MarketplaceUiState>(MarketplaceUiState.Loading)
    val uiState: StateFlow<MarketplaceUiState> = _uiState.asStateFlow()

    private val _selectedListing = MutableStateFlow<ListingDetail?>(null)
    val selectedListing: StateFlow<ListingDetail?> = _selectedListing.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _translateError = MutableStateFlow<String?>(null)
    val translateError: StateFlow<String?> = _translateError.asStateFlow()

    private val _creatorProfile = MutableStateFlow<CreatorProfile?>(null)
    /** Backs [CreatorListingsScreen]'s profile header (avatar, stats, badges) — separate from
     * [uiState]'s listing grid so the header can render/update independently of pagination. */
    val creatorProfile: StateFlow<CreatorProfile?> = _creatorProfile.asStateFlow()

    private var currentFilter = MarketplaceFilter()

    init { loadListings() }

    fun loadListings(filter: MarketplaceFilter = currentFilter) {
        currentFilter = filter
        viewModelScope.launch {
            _uiState.value = MarketplaceUiState.Loading
            try {
                backendClient.getListings(
                    type = filter.type, genre = filter.genre, maturity = filter.maturity,
                    sort = filter.sort, page = filter.page, pageSize = filter.pageSize,
                    includeMature = filter.includeMature, search = filter.search, creatorId = filter.creatorId
                ).fold(
                    onSuccess = { response -> _uiState.value = MarketplaceUiState.Ready(response.listings) },
                    onFailure = { e -> _uiState.value = MarketplaceUiState.Error(e.message ?: appContext.getString(R.string.error_failed_to_load_listings)) }
                )
            } catch (e: Exception) {
                _uiState.value = MarketplaceUiState.Error(e.message ?: appContext.getString(R.string.error_unknown))
            }
        }
    }

    /** Toggles following [creatorId] from a grid card rather than the listing detail screen — see
     * [toggleFollowCreator] for that path. Updates every card for this creator currently in
     * [_uiState] (a creator can have several listings visible in the same grid at once), not just
     * the one tapped, so they don't visually disagree with each other. */
    fun toggleFollowCreatorInGrid(creatorId: String, currentlyFollowing: Boolean) {
        viewModelScope.launch {
            val result = if (currentlyFollowing) {
                backendClient.unfollowCreator(creatorId)
            } else {
                backendClient.followCreator(creatorId)
            }
            result.onSuccess { status ->
                val state = _uiState.value as? MarketplaceUiState.Ready ?: return@onSuccess
                _uiState.value = state.copy(
                    listings = state.listings.map { listing ->
                        if (listing.creatorId == creatorId) listing.copy(isFollowingCreator = status.following) else listing
                    }
                )
            }
        }
    }

    /** Loads the aggregate creator profile (badges/progress/stats/follower count) shown in
     * [CreatorListingsScreen]'s header. Silently leaves [creatorProfile] `null` on failure — the
     * screen already falls back to the first-listing-derived title/grid, so a header that never
     * appears is a degraded-but-usable state, not a blocking error. */
    fun loadCreatorProfile(creatorId: String) {
        viewModelScope.launch {
            backendClient.getCreatorProfile(creatorId).fold(
                onSuccess = { profile -> _creatorProfile.value = profile },
                onFailure = { _creatorProfile.value = null }
            )
        }
    }

    fun loadListingDetail(listingId: String) {
        viewModelScope.launch {
            backendClient.getListing(listingId).fold(
                onSuccess = { detail -> _selectedListing.value = detail },
                onFailure = { }
            )
        }
    }

    /** Translates the persona sheet of the currently displayed listing into the app's current
     * language with the user's own translation model. This only swaps the displayed fields in
     * [_selectedListing] (the name is left untranslated) — nothing is persisted, since the listing
     * isn't owned by the user yet. **Personas only.** */
    fun translateListing(listingId: String) {
        if (_isTranslating.value) return
        val current = _selectedListing.value ?: return
        val currentPersona = current.personaData ?: return
        val targetLanguage = appLanguageManager.getSelectedLanguage()

        viewModelScope.launch {
            _isTranslating.value = true
            _translateError.value = null

            translatePersonaUseCase(
                shortDescription = currentPersona.shortDescription,
                personality = currentPersona.personality,
                scenario = currentPersona.scenario,
                firstMessage = currentPersona.firstMessage,
                exampleDialogues = currentPersona.exampleDialogues,
                targetLanguage = targetLanguage
            ).fold(
                onSuccess = { result ->
                    _selectedListing.value = current.copy(
                        personaData = currentPersona.copy(
                            shortDescription = result.shortDescription,
                            personality = result.personality,
                            scenario = result.scenario,
                            firstMessage = result.firstMessage,
                            exampleDialogues = result.exampleDialogues
                        )
                    )
                },
                onFailure = { e ->
                    _translateError.value = e.message ?: appContext.getString(R.string.listing_detail_error_translation_fallback)
                }
            )
            _isTranslating.value = false
        }
    }

    fun dismissTranslateError() {
        _translateError.value = null
    }

    fun downloadListing(listingId: String, onResult: (DownloadOutcome) -> Unit) {
        viewModelScope.launch {
            val alreadyOwned = withContext(Dispatchers.IO) {
                personaRepository.getBySourceListingId(listingId) != null ||
                    universeRepository.getBySourceListingId(listingId) != null
            }
            if (alreadyOwned) {
                onResult(DownloadOutcome.AlreadyOwned)
                return@launch
            }
            try {
                backendClient.downloadListing(listingId).fold(
                    onSuccess = { download -> importListing(listingId, download); onResult(DownloadOutcome.Success) },
                    onFailure = { onResult(DownloadOutcome.Failed) }
                )
            } catch (e: Exception) {
                onResult(DownloadOutcome.Failed)
            }
        }
    }

    private suspend fun importListing(listingId: String, download: ListingDownload) = withContext(Dispatchers.IO) {
        when (download.type) {
            "PERSONA" -> download.personaData?.let { importPersona(listingId, it, download.imageUrls, download.tags) }
            "UNIVERSE" -> download.universeData?.let { importUniverse(listingId, it, download.imageUrls, download.tags) }
        }
    }

    private suspend fun importPersona(listingId: String, data: MarketplacePersonaData, imageUrls: List<String>, tags: List<String> = emptyList()) {
        val now = System.currentTimeMillis()
        val personaId = UUID.randomUUID().toString()

        // Decode and save avatar
        var avatarImageId: String? = null
        if (data.avatarImageBase64 != null) {
            val bytes = Base64.decode(data.avatarImageBase64, Base64.NO_WRAP)
            avatarImageId = encryptedImageStore.save(bytes)
        } else if (imageUrls.isNotEmpty()) {
            val bytes = downloadImageBytes(imageUrls.first())
            if (bytes != null) avatarImageId = encryptedImageStore.save(bytes)
        }

        // Parse maturity tags
        val maturityTags = data.maturityTags.mapNotNull { name ->
            runCatching { MaturityTag.valueOf(name) }.getOrNull()
        }

        // Create persona
        personaRepository.upsert(
            PersonaEntity(
                id = personaId,
                universeId = null,
                name = data.name,
                shortDescription = data.shortDescription,
                personality = data.personality,
                scenario = data.scenario,
                firstMessage = data.firstMessage,
                exampleDialogues = data.exampleDialogues,
                age = data.age,
                maturityTags = maturityTags,
                tags = tags,
                visualSheetJson = data.visualSheetJson,
                // The inner drives were added to PersonaEntity on 2026-08-23 but not to this import
                // path, so a persona bought from the marketplace arrived without the five fields that
                // most shape how it behaves under pressure.
                desire = data.desire,
                fear = data.fear,
                flaw = data.flaw,
                moralLine = data.moralLine,
                secret = data.secret,
                avatarImageId = avatarImageId,
                sourceListingId = listingId,
                version = 1,
                createdAt = now,
                updatedAt = now
            )
        )

        // The author's own story tones travel with the persona (2026-08-24): whoever installs it gets
        // the registers it was written for, already set up, instead of six generic recipes.
        decodeToneCards(data.toneCardsJson).forEach { payload ->
            toneCardRepository.upsert(payload.toEntity(personaId = personaId, createdAt = now))
        }
        // The openings the author wrote for this character: often the reason the persona is worth
        // installing at all, and until now the one piece of its sheet the listing silently dropped.
        decodeEntryScenes(data.entryScenesJson).forEach { payload ->
            entrySceneRepository.upsert(payload.toEntity(personaId = personaId, createdAt = now))
        }

        // Import gallery images
        data.galleryImageBase64List.forEach { base64 ->
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val storeId = encryptedImageStore.save(bytes)
            personaImageRepository.upsert(
                PersonaImageEntity(
                    id = UUID.randomUUID().toString(),
                    personaId = personaId,
                    imageStoreId = storeId,
                    description = "",
                    createdAt = now
                )
            )
        }

        // Also save remaining imageUrls as gallery
        imageUrls.drop(if (data.avatarImageBase64 != null) 0 else 1).forEach { url ->
            val bytes = downloadImageBytes(url)
            if (bytes != null) {
                val storeId = encryptedImageStore.save(bytes)
                personaImageRepository.upsert(
                    PersonaImageEntity(
                        id = UUID.randomUUID().toString(),
                        personaId = personaId,
                        imageStoreId = storeId,
                        description = "",
                        createdAt = now
                    )
                )
            }
        }
    }

    private suspend fun importUniverse(listingId: String, data: MarketplaceUniverseData, imageUrls: List<String>, tags: List<String> = emptyList()) {
        val now = System.currentTimeMillis()
        val universeId = UUID.randomUUID().toString()

        // Decode and save avatar
        var avatarImageId: String? = null
        if (data.avatarImageBase64 != null) {
            val bytes = Base64.decode(data.avatarImageBase64, Base64.NO_WRAP)
            avatarImageId = encryptedImageStore.save(bytes)
        } else if (imageUrls.isNotEmpty()) {
            val bytes = downloadImageBytes(imageUrls.first())
            if (bytes != null) avatarImageId = encryptedImageStore.save(bytes)
        }

        // Create universe
        universeRepository.insert(
            UniverseEntity(
                id = universeId,
                name = data.name,
                description = data.description,
                genre = data.genre,
                visualStyle = data.visualStyle,
                tags = tags,
                avatarImageId = avatarImageId,
                sourceListingId = listingId,
                createdAt = now,
                updatedAt = now
            )
        )

        // Import remaining images into the universe's own gallery
        imageUrls.drop(if (data.avatarImageBase64 != null) 0 else 1).forEach { url ->
            val bytes = downloadImageBytes(url)
            if (bytes != null) {
                val storeId = encryptedImageStore.save(bytes)
                universeImageRepository.upsert(
                    UniverseImageEntity(
                        id = UUID.randomUUID().toString(),
                        universeId = universeId,
                        imageStoreId = storeId,
                        description = "",
                        createdAt = now
                    )
                )
            }
        }

        // Import factions
        data.factions.forEach { faction ->
            factionRepository.insert(
                FactionEntity(
                    id = UUID.randomUUID().toString(),
                    universeId = universeId,
                    name = faction.name,
                    description = faction.description,
                    type = faction.type,
                    alignment = faction.alignment,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // Import locations
        data.locations.forEach { location ->
            locationRepository.insert(
                LocationEntity(
                    id = UUID.randomUUID().toString(),
                    universeId = universeId,
                    name = location.name,
                    description = location.description,
                    type = location.type,
                    parentLocationId = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // Import NPCs
        data.npcs.forEach { npc ->
            npcRepository.insert(
                NpcEntity(
                    id = UUID.randomUUID().toString(),
                    universeId = universeId,
                    name = npc.name,
                    description = npc.description,
                    personality = npc.personality,
                    role = npc.role,
                    factionId = null,
                    locationId = null,
                    age = npc.age,
                    physicalDescription = npc.physicalDescription,
                    importance = 1,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // Import personas
        data.personas.forEach { personaData ->
            val personaId = UUID.randomUUID().toString()
            var avatarImageId: String? = null

            // Avatar from base64 or first image URL
            if (personaData.avatarImageBase64 != null) {
                val bytes = Base64.decode(personaData.avatarImageBase64, Base64.NO_WRAP)
                avatarImageId = encryptedImageStore.save(bytes)
            } else if (imageUrls.isNotEmpty()) {
                val bytes = downloadImageBytes(imageUrls.first())
                if (bytes != null) avatarImageId = encryptedImageStore.save(bytes)
            }

            val maturityTags = personaData.maturityTags.mapNotNull { name ->
                runCatching { MaturityTag.valueOf(name) }.getOrNull()
            }

            personaRepository.upsert(
                PersonaEntity(
                    id = personaId,
                    universeId = universeId,
                    name = personaData.name,
                    shortDescription = personaData.shortDescription,
                    personality = personaData.personality,
                    scenario = personaData.scenario,
                    firstMessage = personaData.firstMessage,
                    exampleDialogues = personaData.exampleDialogues,
                    age = personaData.age,
                    maturityTags = maturityTags,
                    visualSheetJson = personaData.visualSheetJson,
                    desire = personaData.desire,
                    fear = personaData.fear,
                    flaw = personaData.flaw,
                    moralLine = personaData.moralLine,
                    secret = personaData.secret,
                    avatarImageId = avatarImageId,
                    // Same provenance marker as the universe itself above — without it, a persona
                    // pulled in as part of a downloaded universe could be republished as if it
                    // were the user's own creation (see PersonaDetailViewModel.publishToMarketplace).
                    sourceListingId = listingId,
                    version = 1,
                    createdAt = now,
                    updatedAt = now
                )
            )

            // A persona arriving inside a universe keeps its authored tones exactly as one bought on
            // its own does — the two import paths must not disagree about what a persona is.
            decodeToneCards(personaData.toneCardsJson).forEach { payload ->
                toneCardRepository.upsert(payload.toEntity(personaId = personaId, createdAt = now))
            }
            decodeEntryScenes(personaData.entryScenesJson).forEach { payload ->
                entrySceneRepository.upsert(payload.toEntity(personaId = personaId, createdAt = now))
            }
        }
    }

    private suspend fun downloadImageBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun createListing(request: CreateListingRequest, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            backendClient.createListing(request).fold(
                onSuccess = { response -> onResult(response.listingId) },
                onFailure = { onResult(null) }
            )
        }
    }

    fun addReview(listingId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            backendClient.addReview(listingId, CreateReviewRequest(rating, comment)).fold(
                onSuccess = { loadListingDetail(listingId) },
                onFailure = { }
            )
        }
    }

    fun reportListing(listingId: String, reason: String, description: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            backendClient.reportListing(listingId, ReportRequest(reason, description)).fold(
                onSuccess = { onResult(true) },
                onFailure = { onResult(false) }
            )
        }
    }

    /** Toggles following [creatorId] for the currently displayed listing — updates
     * [_selectedListing]'s `isFollowingCreator` optimistically-but-only-on-success (reverted to the
     * prior value on failure, e.g. offline) rather than flipping it before the call resolves, since
     * there's no loading affordance on this single button to cover a wrong guess. */
    fun toggleFollowCreator(creatorId: String, currentlyFollowing: Boolean) {
        val listing = _selectedListing.value ?: return
        viewModelScope.launch {
            val result = if (currentlyFollowing) {
                backendClient.unfollowCreator(creatorId)
            } else {
                backendClient.followCreator(creatorId)
            }
            result.onSuccess { status ->
                if (_selectedListing.value?.id == listing.id) {
                    _selectedListing.value = _selectedListing.value?.copy(isFollowingCreator = status.following)
                }
            }
        }
    }
}

data class MarketplaceFilter(
    val type: String? = null,
    val genre: String? = null,
    val maturity: String? = null,
    val sort: String? = null,
    val page: Int = 0,
    val pageSize: Int = 20,
    val includeMature: Boolean = false,
    val search: String? = null,
    val creatorId: String? = null
)

sealed interface DownloadOutcome {
    data object Success : DownloadOutcome
    data object AlreadyOwned : DownloadOutcome
    data object Failed : DownloadOutcome
}

sealed class MarketplaceUiState {
    data object Loading : MarketplaceUiState()
    data class Ready(val listings: List<ListingSummary>) : MarketplaceUiState()
    data class Error(val message: String) : MarketplaceUiState()
}