package com.kitsune.feature.persona.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.model.CreateListingRequest
import com.kitsune.core.backend.model.MarketplacePersonaData
import com.kitsune.core.data.local.entities.EntrySceneEntity
import com.kitsune.core.data.local.entities.ToneCardEntity
import com.kitsune.core.data.local.entities.encodeToneCards
import com.kitsune.core.data.local.entities.encodeEntryScenes
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.PersonaImageEntity
import com.kitsune.core.data.repository.EntrySceneRepository
import com.kitsune.core.data.repository.ToneCardRepository
import com.kitsune.core.data.repository.PersonaImageRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.core.network.persona.GenerateEntrySceneUseCase
import com.kitsune.core.network.persona.SceneDraft
import com.kitsune.core.security.storage.EncryptedImageStore
import com.kitsune.feature.persona.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import android.content.Context

sealed interface PersonaDetailUiState {
    data object Loading : PersonaDetailUiState
    data class Ready(
        val persona: PersonaEntity,
        val entryScenes: List<EntrySceneEntity>,
        val galleryImageEntities: List<PersonaImageEntity>,
        val selectedSceneId: String?
    ) : PersonaDetailUiState
    data class Error(val message: String) : PersonaDetailUiState
}

data class GalleryImage(
    val entity: PersonaImageEntity,
    val bytes: ByteArray?
)

data class ChatStartEvent(
    val chatId: String,
    val sceneId: String?
)

@HiltViewModel
class PersonaDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val personaRepository: PersonaRepository,
    private val entrySceneRepository: EntrySceneRepository,
    private val toneCardRepository: ToneCardRepository,
    private val personaImageRepository: PersonaImageRepository,
    private val chatRepository: ChatRepository,
    private val encryptedImageStore: EncryptedImageStore,
    private val backendClient: KitsuneBackendClient,
    private val generateEntrySceneUseCase: GenerateEntrySceneUseCase
) : ViewModel() {

    private val personaId: String = checkNotNull(savedStateHandle["personaId"])

    private val _uiState = MutableStateFlow<PersonaDetailUiState>(PersonaDetailUiState.Loading)
    val uiState: StateFlow<PersonaDetailUiState> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _startChatEvent = MutableStateFlow<ChatStartEvent?>(null)
    val startChatEvent: StateFlow<ChatStartEvent?> = _startChatEvent.asStateFlow()

    private val _isStartingChat = MutableStateFlow(false)
    val isStartingChat: StateFlow<Boolean> = _isStartingChat.asStateFlow()

    val entryScenes: StateFlow<List<EntrySceneEntity>> = entrySceneRepository.observeByPersona(personaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** This persona's saved ways of telling stories (2026-08-24) — offered by the story card when a
     *  conversation starts, and shipped with the persona on export and on the marketplace. */
    val toneCards: StateFlow<List<ToneCardEntity>> = toneCardRepository.observeByPersona(personaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Creates or replaces a tone card.
     *
     * `basePresetId` stays empty for a hand-authored card: the decoder profile is then derived from
     * its tone rather than borrowed from a built-in recipe, which is the right source for a register
     * the author defined themselves.
     */
    fun saveToneCard(existingId: String?, draft: ToneCardDraft) {
        viewModelScope.launch {
            toneCardRepository.upsert(
                ToneCardEntity(
                    id = existingId ?: UUID.randomUUID().toString(),
                    personaId = personaId,
                    name = draft.name,
                    description = draft.description,
                    directive = draft.directive,
                    storyPaceMode = draft.storyPaceMode,
                    toneMode = draft.toneMode,
                    involvementMode = draft.involvementMode,
                    narrativeRhythmMode = draft.narrativeRhythmMode,
                    universeMode = draft.universeMode,
                    intensityMode = draft.intensityMode,
                    replyLength = draft.replyLength,
                    narrationBalance = draft.narrationBalance,
                    voiceMode = draft.voiceMode,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteToneCard(card: ToneCardEntity) {
        viewModelScope.launch { toneCardRepository.delete(card) }
    }

    private val _galleryBytesById = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val galleryBytesById: StateFlow<Map<String, ByteArray>> = _galleryBytesById.asStateFlow()

    private val _avatarBytes = MutableStateFlow<ByteArray?>(null)
    val avatarBytes: StateFlow<ByteArray?> = _avatarBytes.asStateFlow()

    private var _selectedSceneId: String? = null

    init {
        loadPersona()
    }

    private fun loadPersona() {
        viewModelScope.launch {
            val persona = personaRepository.getById(personaId)
            if (persona == null) {
                _uiState.value = PersonaDetailUiState.Error("Persona introuvable")
                return@launch
            }

            withContext(Dispatchers.IO) {
                persona.avatarImageId?.let { _avatarBytes.value = encryptedImageStore.load(it) }
            }

            _uiState.value = PersonaDetailUiState.Ready(
                persona = persona,
                entryScenes = entrySceneRepository.getByPersona(personaId),
                galleryImageEntities = emptyList(),
                selectedSceneId = null
            )
        }
    }

    fun refreshState() {
        viewModelScope.launch {
            val persona = personaRepository.getById(personaId) ?: return@launch
            val scenes = entrySceneRepository.getByPersona(personaId)
            val images = personaImageRepository.getByPersona(personaId)

            val missingIds = images.map { it.imageStoreId }.filter { it !in _galleryBytesById.value }
            if (missingIds.isNotEmpty()) {
                val loaded = withContext(Dispatchers.IO) {
                    missingIds.mapNotNull { id -> encryptedImageStore.load(id)?.let { id to it } }
                }
                _galleryBytesById.value = _galleryBytesById.value + loaded
            }

            _uiState.value = PersonaDetailUiState.Ready(
                persona = persona,
                entryScenes = scenes,
                galleryImageEntities = images,
                selectedSceneId = _selectedSceneId
            )
        }
    }

    fun ensureGalleryImagesLoaded() {
        viewModelScope.launch {
            val state = _uiState.value as? PersonaDetailUiState.Ready ?: return@launch
            val images = state.galleryImageEntities
            val missingIds = images.map { it.imageStoreId }.filter { it !in _galleryBytesById.value }
            if (missingIds.isNotEmpty()) {
                val loaded = withContext(Dispatchers.IO) {
                    missingIds.mapNotNull { id -> encryptedImageStore.load(id)?.let { id to it } }
                }
                _galleryBytesById.value = _galleryBytesById.value + loaded
                refreshState()
            }
        }
    }

    fun selectScene(sceneId: String?) {
        _selectedSceneId = sceneId
        val state = _uiState.value as? PersonaDetailUiState.Ready ?: return
        _uiState.value = state.copy(selectedSceneId = sceneId)
    }

    fun createEntryScene(title: String, scenario: String, firstMessage: String) {
        viewModelScope.launch {
            val scene = EntrySceneEntity(
                id = UUID.randomUUID().toString(),
                personaId = personaId,
                title = title,
                scenario = scenario,
                firstMessage = firstMessage,
                createdAt = System.currentTimeMillis()
            )
            entrySceneRepository.upsert(scene)
            refreshState()
        }
    }

    suspend fun generateEntryScene(description: String): Result<SceneDraft> {
        val persona = (_uiState.value as? PersonaDetailUiState.Ready)?.persona
            ?: return Result.failure(IllegalStateException("Persona not loaded"))
        val characterContext = "Name: ${persona.name}\n" +
            "Description: ${persona.shortDescription}\n" +
            "Personality: ${persona.personality}"
        return generateEntrySceneUseCase(characterContext, description)
    }

    fun deleteEntryScene(scene: EntrySceneEntity) {
        viewModelScope.launch {
            entrySceneRepository.delete(scene)
            if (_selectedSceneId == scene.id) _selectedSceneId = null
            refreshState()
        }
    }

    fun addTag(tag: String) {
        val cleaned = tag.trim().lowercase().replace(",", "")
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            val state = (_uiState.value as? PersonaDetailUiState.Ready) ?: return@launch
            if (cleaned in state.persona.tags) return@launch
            val updated = state.persona.copy(tags = state.persona.tags + cleaned, updatedAt = System.currentTimeMillis())
            personaRepository.upsert(updated)
            _uiState.value = state.copy(persona = updated)
        }
    }

    fun removeTag(tag: String) {
        viewModelScope.launch {
            val state = (_uiState.value as? PersonaDetailUiState.Ready) ?: return@launch
            val updated = state.persona.copy(tags = state.persona.tags - tag, updatedAt = System.currentTimeMillis())
            personaRepository.upsert(updated)
            _uiState.value = state.copy(persona = updated)
        }
    }

    /**
     * Les cinq champs d'intériorité dramatique (2026-08-22), édités un par un.
     *
     * Un seul point d'entrée plutôt que cinq méthodes quasi identiques : le `when` reste exhaustif,
     * donc ajouter un trait plus tard casse la compilation au lieu de produire une sauvegarde
     * silencieusement sans effet.
     *
     * Ces champs sont générés automatiquement à la création du persona, mais ce sont ceux qui
     * pèsent le plus sur le comportement du modèle en jeu — d'où l'édition manuelle : c'est ici que
     * l'utilisateur corrige un désir mal deviné ou durcit une ligne morale.
     */
    fun updateInteriority(trait: InteriorityTrait, value: String) {
        viewModelScope.launch {
            val state = (_uiState.value as? PersonaDetailUiState.Ready) ?: return@launch
            val cleaned = value.trim()
            val persona = state.persona
            if (trait.read(persona) == cleaned) return@launch
            val updated = trait.write(persona, cleaned).copy(updatedAt = System.currentTimeMillis())
            personaRepository.upsert(updated)
            _uiState.value = state.copy(persona = updated)
        }
    }

    fun deleteGalleryImage(image: GalleryImage) {
        viewModelScope.launch {
            personaImageRepository.delete(image.entity)
            withContext(Dispatchers.IO) { encryptedImageStore.delete(image.entity.imageStoreId) }
            _galleryBytesById.value = _galleryBytesById.value - image.entity.imageStoreId
            refreshState()
        }
    }

    fun setAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            val state = (_uiState.value as? PersonaDetailUiState.Ready) ?: return@launch
            val newId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
            val oldId = state.persona.avatarImageId
            val updated = state.persona.copy(avatarImageId = newId, updatedAt = System.currentTimeMillis())
            personaRepository.upsert(updated)
            _avatarBytes.value = bytes
            _uiState.value = state.copy(persona = updated)
            if (oldId != null) withContext(Dispatchers.IO) { encryptedImageStore.delete(oldId) }
        }
    }

    fun deletePersona(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val state = (_uiState.value as? PersonaDetailUiState.Ready) ?: return@launch
            withContext(Dispatchers.IO) {
                // Supprimer l'avatar
                state.persona.avatarImageId?.let { encryptedImageStore.delete(it) }
                // Supprimer les images de galerie
                val galleryImages = personaImageRepository.getByPersona(personaId)
                galleryImages.forEach { image ->
                    encryptedImageStore.delete(image.imageStoreId)
                }
            }
            personaRepository.delete(state.persona)
            onDeleted()
        }
    }

    fun startChat() {
        if (_isStartingChat.value) {
            return
        }
        viewModelScope.launch {
            _isStartingChat.value = true
            try {
                val chat = chatRepository.createChat(personaId, title = "", selectedSceneId = _selectedSceneId)
                _startChatEvent.value = ChatStartEvent(chat.id, _selectedSceneId)
            } finally {
                _isStartingChat.value = false
            }
        }
    }

    fun consumeStartChatEvent() {
        _startChatEvent.value = null
    }

    fun dismissError() {
        _error.value = null
    }

    fun setError(message: String) {
        _error.value = message
    }

    suspend fun publishToMarketplace(persona: PersonaEntity, description: String) {
        // A persona downloaded from the marketplace (sourceListingId set at import time, see
        // MarketplaceViewModel.importPersona/importUniverse) must never be republishable as if it
        // were the user's own creation — the UI hides the toggle for this case too (see
        // PersonaDetailScreen), this is the defense-in-depth backstop.
        check(persona.sourceListingId == null) { "Ce persona provient du marketplace et ne peut pas être republié." }

        val ready = _uiState.value as? PersonaDetailUiState.Ready ?: return

        val avatarBytes = persona.avatarImageId?.let { encryptedImageStore.load(it) }
        val avatarBase64 = avatarBytes?.let { bytes ->
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }

        val galleryBase64List = ready.galleryImageEntities.mapNotNull { imgEntity ->
            encryptedImageStore.load(imgEntity.imageStoreId)?.let { bytes ->
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        }

        val personaData = MarketplacePersonaData(
            name = persona.name,
            shortDescription = persona.shortDescription,
            personality = persona.personality,
            scenario = persona.scenario,
            firstMessage = persona.firstMessage,
            exampleDialogues = persona.exampleDialogues,
            age = persona.age,
            maturityTags = persona.maturityTags.map { it.name },
            visualSheetJson = persona.visualSheetJson,
            avatarImageBase64 = avatarBase64,
            galleryImageBase64List = galleryBase64List,
            // What makes a published persona play the way its author intended: the tones they wrote
            // for it, and the inner drives that decide how it behaves under pressure.
            toneCardsJson = encodeToneCards(toneCardRepository.getByPersona(personaId)),
            entryScenesJson = encodeEntryScenes(entrySceneRepository.getByPersona(personaId)),
            desire = persona.desire,
            fear = persona.fear,
            flaw = persona.flaw,
            moralLine = persona.moralLine,
            secret = persona.secret
        )

        val imageBase64List = mutableListOf<String>()
        avatarBase64?.let { imageBase64List.add(it) }
        imageBase64List.addAll(galleryBase64List)

        val request = CreateListingRequest(
            type = "PERSONA",
            title = persona.name,
            description = description,
            maturityRating = persona.maturityTags.firstOrNull()?.name ?: "SFW",
            tags = persona.tags,
            personaData = personaData,
            imageBase64List = imageBase64List
        )

        backendClient.createListing(request).fold(
            onSuccess = {},
            onFailure = { throw it }
        )
    }

    suspend fun unpublishFromMarketplace(personaId: String) {
        backendClient.getMyListings().onSuccess { response ->
            response.listings
                .filter { it.type == "PERSONA" && it.title == (_uiState.value as? PersonaDetailUiState.Ready)?.persona?.name }
                .forEach { listing ->
                    backendClient.deleteListing(listing.id)
                }
        }
    }

    suspend fun checkIfPublished(persona: PersonaEntity): Boolean {
        var result = false
        backendClient.getMyListings().onSuccess { response ->
            result = response.listings.any { it.type == "PERSONA" && it.title == persona.name }
        }
        return result
    }
}
