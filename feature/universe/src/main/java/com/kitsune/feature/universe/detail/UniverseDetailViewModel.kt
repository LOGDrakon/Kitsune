package com.kitsune.feature.universe.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.FactionEntity
import com.kitsune.core.data.local.entities.LocationEntity
import com.kitsune.core.data.local.entities.NpcEntity
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.UniverseEntity
import com.kitsune.core.data.local.entities.UniverseImageEntity
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.FactionRepository
import com.kitsune.core.data.repository.LocationRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.data.repository.ToneCardRepository
import com.kitsune.core.data.repository.EntrySceneRepository
import com.kitsune.core.data.local.entities.encodeEntryScenes
import com.kitsune.core.data.local.entities.encodeToneCards
import com.kitsune.core.data.repository.UniverseImageRepository
import com.kitsune.core.data.repository.UniverseRepository
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.model.CreateListingRequest
import com.kitsune.core.backend.model.MarketplaceFactionData
import com.kitsune.core.backend.model.MarketplaceLocationData
import com.kitsune.core.backend.model.MarketplaceNpcData
import com.kitsune.core.backend.model.MarketplacePersonaData
import com.kitsune.core.backend.model.MarketplaceUniverseData
import com.kitsune.core.network.repository.FactionDraft
import com.kitsune.core.network.repository.GenerateImageUseCase
import com.kitsune.core.network.repository.GenerateWorldElementUseCase
import com.kitsune.core.network.repository.LocationDraft
import com.kitsune.core.network.repository.NpcDraft
import com.kitsune.core.security.storage.EncryptedImageStore
import com.kitsune.feature.universe.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

sealed interface UniverseDetailUiState {
    data object Loading : UniverseDetailUiState
    data class Ready(
        val universe: UniverseEntity,
        val locations: List<LocationEntity>,
        val factions: List<FactionEntity>,
        val npcs: List<NpcEntity>,
        val personas: List<PersonaEntity>,
        val ensembleChats: List<ChatEntity>
    ) : UniverseDetailUiState
    data class Error(val message: String) : UniverseDetailUiState
}

@HiltViewModel
class UniverseDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val universeRepository: UniverseRepository,
    private val locationRepository: LocationRepository,
    private val factionRepository: FactionRepository,
    private val npcRepository: NpcRepository,
    private val personaRepository: PersonaRepository,
    private val toneCardRepository: ToneCardRepository,
    private val entrySceneRepository: EntrySceneRepository,
    private val chatRepository: ChatRepository,
    private val universeImageRepository: UniverseImageRepository,
    private val encryptedImageStore: EncryptedImageStore,
    private val generateImageUseCase: GenerateImageUseCase,
    private val generateWorldElementUseCase: GenerateWorldElementUseCase,
    private val backendClient: KitsuneBackendClient
) : ViewModel() {

    private val universeId: String = checkNotNull(savedStateHandle["universeId"])

    private val _uiState = MutableStateFlow<UniverseDetailUiState>(UniverseDetailUiState.Loading)
    val uiState: StateFlow<UniverseDetailUiState> = _uiState.asStateFlow()

    private val _avatarBytes = MutableStateFlow<ByteArray?>(null)
    val avatarBytes: StateFlow<ByteArray?> = _avatarBytes.asStateFlow()

    private val _galleryImages = MutableStateFlow<List<UniverseImageEntity>>(emptyList())
    val galleryImages: StateFlow<List<UniverseImageEntity>> = _galleryImages.asStateFlow()

    private val _galleryBytesById = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val galleryBytesById: StateFlow<Map<String, ByteArray>> = _galleryBytesById.asStateFlow()

    private val _isGeneratingAvatar = MutableStateFlow(false)
    val isGeneratingAvatar: StateFlow<Boolean> = _isGeneratingAvatar.asStateFlow()

    /** Decrypted portrait/illustration bytes for NPCs/locations that have one, keyed by entity id —
     * loaded alongside the rest of the universe since there's no dedicated gallery screen for these
     * (unlike the universe's own avatar) to lazily load them from. */
    private val _npcAvatarBytes = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val npcAvatarBytes: StateFlow<Map<String, ByteArray>> = _npcAvatarBytes.asStateFlow()

    private val _locationAvatarBytes = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val locationAvatarBytes: StateFlow<Map<String, ByteArray>> = _locationAvatarBytes.asStateFlow()

    /** Standalone personas (not yet part of any universe) offered by "Inclure un persona existant" —
     * loaded on demand rather than kept live, since this list is only ever shown inside that one
     * dialog. */
    private val _includablePersonas = MutableStateFlow<List<PersonaEntity>>(emptyList())
    val includablePersonas: StateFlow<List<PersonaEntity>> = _includablePersonas.asStateFlow()

    init {
        loadUniverse()
    }

    /** Re-fetches everything — call when returning to this screen (e.g. after creating a persona
     * for this universe), since the ViewModel survives across that round-trip and won't otherwise
     * notice the new row. */
    fun refresh() = loadUniverse()

    private fun loadUniverse() {
        viewModelScope.launch {
            try {
                val universe = universeRepository.getById(universeId)
                if (universe == null) {
                    _uiState.value = UniverseDetailUiState.Error(context.getString(R.string.universe_detail_error_not_found))
                    return@launch
                }

                val locations = locationRepository.getByUniverse(universeId).first()
                val factions = factionRepository.getByUniverse(universeId).first()
                val npcs = npcRepository.getByUniverse(universeId).first()
                val personas = personaRepository.observeByUniverse(universeId).first()
                val ensembleChats = chatRepository.observeByUniverse(universeId).first()

                _avatarBytes.value = universe.avatarImageId?.let {
                    withContext(Dispatchers.IO) { encryptedImageStore.load(it) }
                }
                val gallery = universeImageRepository.getByUniverse(universeId)
                _galleryImages.value = gallery
                _galleryBytesById.value = withContext(Dispatchers.IO) {
                    gallery.mapNotNull { image -> encryptedImageStore.load(image.imageStoreId)?.let { image.imageStoreId to it } }.toMap()
                }
                _npcAvatarBytes.value = withContext(Dispatchers.IO) {
                    npcs.mapNotNull { npc -> npc.avatarImageId?.let { encryptedImageStore.load(it) }?.let { npc.id to it } }.toMap()
                }
                _locationAvatarBytes.value = withContext(Dispatchers.IO) {
                    locations.mapNotNull { loc -> loc.avatarImageId?.let { encryptedImageStore.load(it) }?.let { loc.id to it } }.toMap()
                }

                _uiState.value = UniverseDetailUiState.Ready(
                    universe = universe,
                    locations = locations,
                    factions = factions,
                    npcs = npcs,
                    personas = personas,
                    ensembleChats = ensembleChats
                )
            } catch (e: Exception) {
                _uiState.value = UniverseDetailUiState.Error(e.message ?: context.getString(R.string.universe_detail_error_unknown))
            }
        }
    }

    /** Loads every standalone persona (not tied to any universe) for the "Inclure un persona
     * existant" dialog — called right before showing it rather than kept live. */
    fun loadIncludablePersonas() {
        viewModelScope.launch {
            _includablePersonas.value = personaRepository.observeAll().first().filter { it.universeId == null }
        }
    }

    /** Ties each of [personaIds] to this universe (requested explicitly: a standalone persona
     * created outside any universe couldn't previously be added to one after the fact — only
     * "create a brand-new persona for this universe" existed). A persona already belongs to at
     * most one universe, so [loadIncludablePersonas] only ever offers ones with no universe yet. */
    fun includePersonas(personaIds: Set<String>) {
        if (personaIds.isEmpty()) return
        viewModelScope.launch {
            personaIds.forEach { id ->
                personaRepository.getById(id)?.let { persona ->
                    personaRepository.upsert(persona.copy(universeId = universeId, updatedAt = System.currentTimeMillis()))
                }
            }
            loadUniverse()
        }
    }

    fun addLocation(name: String, description: String, type: String) {
        viewModelScope.launch {
            try {
                val location = LocationEntity(
                    id = UUID.randomUUID().toString(),
                    universeId = universeId,
                    name = name,
                    description = description,
                    type = type,
                    parentLocationId = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                locationRepository.insert(location)
                loadUniverse()
            } catch (e: Exception) {
                _uiState.value = UniverseDetailUiState.Error(e.message ?: context.getString(R.string.universe_detail_error_add_location))
            }
        }
    }

    fun deleteLocation(location: LocationEntity) {
        viewModelScope.launch {
            try {
                locationRepository.delete(location)
                loadUniverse()
            } catch (e: Exception) {
                _uiState.value = UniverseDetailUiState.Error(e.message ?: context.getString(R.string.universe_detail_error_delete_location))
            }
        }
    }

    fun addFaction(name: String, description: String, type: String, alignment: String?) {
        viewModelScope.launch {
            try {
                val faction = FactionEntity(
                    id = UUID.randomUUID().toString(),
                    universeId = universeId,
                    name = name,
                    description = description,
                    type = type,
                    alignment = alignment,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                factionRepository.insert(faction)
                loadUniverse()
            } catch (e: Exception) {
                _uiState.value = UniverseDetailUiState.Error(e.message ?: context.getString(R.string.universe_detail_error_add_faction))
            }
        }
    }

    fun deleteFaction(faction: FactionEntity) {
        viewModelScope.launch {
            try {
                factionRepository.delete(faction)
                loadUniverse()
            } catch (e: Exception) {
                _uiState.value = UniverseDetailUiState.Error(e.message ?: context.getString(R.string.universe_detail_error_delete_faction))
            }
        }
    }

    fun addNpc(
        name: String,
        description: String,
        personality: String,
        role: String,
        factionId: String?,
        locationId: String?,
        age: Int?,
        physicalDescription: String? = null
    ) {
        viewModelScope.launch {
            try {
                val npc = NpcEntity(
                    id = UUID.randomUUID().toString(),
                    universeId = universeId,
                    name = name,
                    description = description,
                    personality = personality,
                    role = role,
                    factionId = factionId,
                    locationId = locationId,
                    age = age,
                    physicalDescription = physicalDescription,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                npcRepository.insert(npc)
                loadUniverse()
            } catch (e: Exception) {
                _uiState.value = UniverseDetailUiState.Error(e.message ?: context.getString(R.string.universe_detail_error_add_npc))
            }
        }
    }

    fun deleteNpc(npc: NpcEntity) {
        viewModelScope.launch {
            try {
                npcRepository.delete(npc)
                loadUniverse()
            } catch (e: Exception) {
                _uiState.value = UniverseDetailUiState.Error(e.message ?: context.getString(R.string.universe_detail_error_delete_npc))
            }
        }
    }

    /** Context reused for every image generated for this universe or something in it (avatar, NPC
     * portrait, location illustration) so they stay visually consistent with each other. */
    private fun visualContext(universe: UniverseEntity): String =
        listOfNotNull(
            universe.visualStyle?.takeIf { it.isNotBlank() },
            universe.genre.takeIf { it.isNotBlank() }
        ).joinToString(", ").ifBlank { universe.description }

    /** Generates a new cover image for the universe, files it into the universe's own gallery (same
     * destination ensemble-chat group scenes already use, see `ImageGenerationViewModel`), and sets
     * it as the avatar right away. */
    fun generateUniverseAvatar(description: String) {
        val universe = (_uiState.value as? UniverseDetailUiState.Ready)?.universe ?: return
        if (_isGeneratingAvatar.value) return

        viewModelScope.launch {
            _isGeneratingAvatar.value = true
            generateImageUseCase(
                characterContext = visualContext(universe),
                description = description.ifBlank { "cover illustration for ${universe.name}" }
            ).onSuccess { images ->
                images.firstOrNull()?.let { bytes ->
                    val galleryId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
                    universeImageRepository.upsert(
                        UniverseImageEntity(
                            id = UUID.randomUUID().toString(),
                            universeId = universeId,
                            imageStoreId = galleryId,
                            description = description,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    setAvatarBytes(universe, bytes)
                }
            }
            _isGeneratingAvatar.value = false
        }
    }

    /** Sets the avatar from a device-picked image (not filed into the universe gallery — mirrors
     * `PersonaDetailViewModel.setAvatar`'s device-picker path). */
    fun setAvatarFromDevice(bytes: ByteArray) {
        val universe = (_uiState.value as? UniverseDetailUiState.Ready)?.universe ?: return
        viewModelScope.launch { setAvatarBytes(universe, bytes) }
    }

    /** Sets the avatar from an existing gallery entry. The bytes are re-saved under a fresh
     * `EncryptedImageStore` id rather than reusing [image]'s id directly — sharing one id between the
     * avatar pointer and a gallery row would mean deleting/replacing one later silently corrupts the
     * other (see `ImageGenerationViewModel.fileIntoChatAndUniverseGalleries`'s doc comment for the
     * same reasoning applied to chat/universe galleries). */
    fun setAvatarFromGallery(image: UniverseImageEntity) {
        val universe = (_uiState.value as? UniverseDetailUiState.Ready)?.universe ?: return
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { encryptedImageStore.load(image.imageStoreId) } ?: return@launch
            setAvatarBytes(universe, bytes)
        }
    }

    fun addTag(tag: String) {
        val cleaned = tag.trim().lowercase().replace(",", "")
        if (cleaned.isBlank()) return
        val universe = (_uiState.value as? UniverseDetailUiState.Ready)?.universe ?: return
        if (cleaned in universe.tags) return
        viewModelScope.launch {
            universeRepository.update(universe.copy(tags = universe.tags + cleaned, updatedAt = System.currentTimeMillis()))
            loadUniverse()
        }
    }

    fun removeTag(tag: String) {
        val universe = (_uiState.value as? UniverseDetailUiState.Ready)?.universe ?: return
        viewModelScope.launch {
            universeRepository.update(universe.copy(tags = universe.tags - tag, updatedAt = System.currentTimeMillis()))
            loadUniverse()
        }
    }

    private suspend fun setAvatarBytes(universe: UniverseEntity, bytes: ByteArray) {
        val newId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
        val oldId = universe.avatarImageId
        val updated = universe.copy(avatarImageId = newId, updatedAt = System.currentTimeMillis())
        universeRepository.update(updated)
        _avatarBytes.value = bytes
        if (oldId != null) withContext(Dispatchers.IO) { encryptedImageStore.delete(oldId) }
        loadUniverse()
    }

    private val _generatingPortraitFor = MutableStateFlow<String?>(null)
    val generatingPortraitFor: StateFlow<String?> = _generatingPortraitFor.asStateFlow()

    /** Generates (or regenerates) a single portrait for [npc], using [physicalDescription] plus the
     * universe's own visual context so it stays consistent with everything else generated for this
     * universe — the same context this NPC's dialogue/scene images already use once saved here (see
     * `ImageGenerationViewModel.buildCastImageContext`). No dedicated gallery, unlike personas/
     * universes: regenerating simply replaces the previous portrait. */
    fun generateNpcImage(npc: NpcEntity, description: String) {
        val universe = (_uiState.value as? UniverseDetailUiState.Ready)?.universe ?: return
        if (_generatingPortraitFor.value != null) return

        viewModelScope.launch {
            _generatingPortraitFor.value = npc.id
            val context = listOfNotNull(
                "${npc.name} (${npc.role}): ${npc.description}",
                npc.physicalDescription?.takeIf { it.isNotBlank() }?.let { "Apparence : $it" },
                visualContext(universe)
            ).joinToString("\n")

            generateImageUseCase(
                characterContext = context,
                description = description.ifBlank { "portrait of ${npc.name}" }
            ).onSuccess { images ->
                images.firstOrNull()?.let { bytes ->
                    val newId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
                    val oldId = npc.avatarImageId
                    npcRepository.update(npc.copy(avatarImageId = newId, updatedAt = System.currentTimeMillis()))
                    if (oldId != null) withContext(Dispatchers.IO) { encryptedImageStore.delete(oldId) }
                    loadUniverse()
                }
            }
            _generatingPortraitFor.value = null
        }
    }

    /** Same idea as [generateNpcImage] but for a location illustration. */
    fun generateLocationImage(location: LocationEntity, description: String) {
        val universe = (_uiState.value as? UniverseDetailUiState.Ready)?.universe ?: return
        if (_generatingPortraitFor.value != null) return

        viewModelScope.launch {
            _generatingPortraitFor.value = location.id
            val context = listOfNotNull(
                "${location.name} (${location.type}): ${location.description}",
                visualContext(universe)
            ).joinToString("\n")

            generateImageUseCase(
                characterContext = context,
                description = description.ifBlank { "illustration of ${location.name}" }
            ).onSuccess { images ->
                images.firstOrNull()?.let { bytes ->
                    val newId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
                    val oldId = location.avatarImageId
                    locationRepository.update(location.copy(avatarImageId = newId, updatedAt = System.currentTimeMillis()))
                    if (oldId != null) withContext(Dispatchers.IO) { encryptedImageStore.delete(oldId) }
                    loadUniverse()
                }
            }
            _generatingPortraitFor.value = null
        }
    }

    suspend fun unpublishFromMarketplace(universeId: String) {
        backendClient.getMyListings().onSuccess { response ->
            response.listings
                .filter { it.type == "UNIVERSE" && it.title == (_uiState.value as? UniverseDetailUiState.Ready)?.universe?.name }
                .forEach { listing -> backendClient.deleteListing(listing.id) }
        }
    }

    suspend fun checkIfPublished(universe: UniverseEntity): Boolean {
        var result = false
        backendClient.getMyListings().onSuccess { response ->
            result = response.listings.any { it.type == "UNIVERSE" && it.title == universe.name }
        }
        return result
    }

    /**
     * Plain suspend functions (not wrapped in [viewModelScope]) so the calling dialog can await the
     * result directly and apply it to its own local form state — mirrors
     * `PersonaExportImportViewModel.exportToFile`'s pattern for the same reason: the loading/error
     * state naturally belongs to that one dialog, not to the screen-wide [uiState].
     */
    suspend fun generateFaction(description: String): Result<FactionDraft> =
        generateWorldElementUseCase.generateFaction(universeContext(), description)

    suspend fun generateLocation(description: String): Result<LocationDraft> =
        generateWorldElementUseCase.generateLocation(universeContext(), description)

    suspend fun generateNpc(description: String): Result<NpcDraft> =
        generateWorldElementUseCase.generateNpc(universeContext(), description)

    private fun universeContext(): String {
        val universe = (_uiState.value as? UniverseDetailUiState.Ready)?.universe ?: return ""
        return listOfNotNull(
            "Nom : ${universe.name}",
            universe.genre.takeIf { it.isNotBlank() }?.let { "Genre : $it" },
            universe.description.takeIf { it.isNotBlank() }?.let { "Description : $it" }
        ).joinToString("\n")
    }

    /** Returns true when the server put the listing in its review queue rather than publishing it. */
    suspend fun publishToMarketplace(
        universe: UniverseEntity,
        factions: List<FactionEntity>,
        locations: List<LocationEntity>,
        npcs: List<NpcEntity>,
        personas: List<PersonaEntity>,
        description: String
    ): Boolean {
        // Same rule as PersonaDetailViewModel: a universe downloaded from the marketplace
        // (sourceListingId set at import time) can never be republished as if it were the user's
        // own creation — the UI hides the toggle for this case too, this is the backstop.
        check(universe.sourceListingId == null) { "Cet univers provient du marketplace et ne peut pas être republié." }

        val avatarBytes = universe.avatarImageId?.let { withContext(Dispatchers.IO) { encryptedImageStore.load(it) } }
        val avatarBase64 = avatarBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }

        val galleryBase64List = withContext(Dispatchers.IO) {
            universeImageRepository.getByUniverse(universe.id).mapNotNull { image ->
                encryptedImageStore.load(image.imageStoreId)?.let { bytes ->
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
            }
        }

        val universeData = MarketplaceUniverseData(
            name = universe.name,
            description = universe.description,
            genre = universe.genre,
            visualStyle = universe.visualStyle,
            avatarImageBase64 = avatarBase64,
            factions = factions.map {
                MarketplaceFactionData(it.name, it.description, it.type, it.alignment)
            },
            locations = locations.map {
                MarketplaceLocationData(it.name, it.description, it.type)
            },
            npcs = npcs.map {
                MarketplaceNpcData(it.name, it.description, it.personality, it.role, it.age, it.physicalDescription)
            },
            personas = personas.map { persona ->
                MarketplacePersonaData(
                    name = persona.name,
                    shortDescription = persona.shortDescription,
                    personality = persona.personality,
                    scenario = persona.scenario,
                    firstMessage = persona.firstMessage,
                    exampleDialogues = persona.exampleDialogues,
                    age = persona.age,
                    maturityTags = persona.maturityTags.map { tag -> tag.name },
                    visualSheetJson = persona.visualSheetJson,
                    // Same payload as publishing a persona on its own: the tones it was written for
                    // and the drives that decide how it behaves. A universe listing that dropped them
                    // would hand buyers a hollower version of the same character.
                    toneCardsJson = encodeToneCards(toneCardRepository.getByPersona(persona.id)),
                    entryScenesJson = encodeEntryScenes(entrySceneRepository.getByPersona(persona.id)),
                    desire = persona.desire,
                    fear = persona.fear,
                    flaw = persona.flaw,
                    moralLine = persona.moralLine,
                    secret = persona.secret
                )
            }
        )

        val imageBase64List = mutableListOf<String>()
        avatarBase64?.let { imageBase64List.add(it) }
        imageBase64List.addAll(galleryBase64List)

        val request = CreateListingRequest(
            type = "UNIVERSE",
            title = universe.name,
            description = description,
            maturityRating = "SFW",
            genre = universe.genre,
            tags = universe.tags,
            universeData = universeData,
            imageBase64List = imageBase64List
        )

        return backendClient.createListing(request).fold(
            onSuccess = { it.inReview },
            onFailure = { throw it }
        )
    }
}
