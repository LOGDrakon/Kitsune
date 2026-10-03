package com.kitsune.feature.chat.imagegen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.common.style.PersonaTemplate
import com.kitsune.core.common.style.PersonaTemplates
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.NpcEntity
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.ChatImageEntity
import com.kitsune.core.data.local.entities.PersonaImageEntity
import com.kitsune.core.data.local.entities.UniverseImageEntity
import com.kitsune.core.data.repository.ChatImageRepository
import com.kitsune.core.data.repository.ChatParticipantRepository
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.PersonaImageRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.data.repository.UniverseImageRepository
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.network.repository.DescribeSceneForImageUseCase
import com.kitsune.core.network.repository.GenerateImageUseCase
import com.kitsune.core.network.repository.ImageGenerationRefusedException
import com.kitsune.core.network.repository.SoftenImagePromptUseCase
import com.kitsune.core.network.visualsheet.EnsurePersonaVisualSheetUseCase
import com.kitsune.core.network.visualsheet.buildPersonaImageContext
import com.kitsune.core.security.storage.EncryptedImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

/** How many recent raw messages give the AI enough context to describe "the current scene" — small on
 * purpose: this is about what's happening *right now*, not the whole story (that's the chat's own
 * summary/lore pipeline, FEATURES.md section 4). */
private const val SCENE_CONTEXT_MESSAGE_COUNT = 8

/** One persona from the current scene's cast that a generated image can be filed under. */
data class GalleryTarget(val personaId: String, val personaName: String)

sealed interface ImageGenerationUiState {
    data object Loading : ImageGenerationUiState
    data class Ready(
        val characterNames: String,
        /** False for ensemble chats (no single avatar to reuse as a reference) or chats with more
         * than one participant — true only for a genuine single-persona chat. */
        val showAvatarReferenceOption: Boolean = true,
        /** Personas in the scene that the generated image can be filed under — empty for a cast made
         * entirely of NPCs (no persona gallery to file it in), one entry for a regular single-persona
         * chat (saved without asking), several for an ensemble chat with more than one persona (the
         * UI asks which one). */
        val galleryTargets: List<GalleryTarget> = emptyList(),
        val description: String = "",
        val selectedStyle: PersonaTemplate? = null,
        val selectedAspectRatio: String? = null,
        val useAvatarReference: Boolean = true,
        /** Niveau de rendu demandé. Standard par défaut : c'est le tarif le plus bas, et le passage
         *  en HD doit rester un choix explicite de l'utilisateur, jamais un réglage subi. */
        val useHdQuality: Boolean = false,
        val isGenerating: Boolean = false,
        val isDescribingScene: Boolean = false,
        val generatedImage: ByteArray? = null,
        /** True right after a generation attempt was declined by the image model's own content
         * policy (not a network/other error) — the UI offers the SFW fallback only in this case. */
        val wasRefused: Boolean = false,
        /** True when [generatedImage] was produced via [SoftenImagePromptUseCase] after an initial
         * refusal, so the UI can flag that it may not exactly match the requested scene. */
        val wasSfwFallback: Boolean = false,
        val error: String? = null
    ) : ImageGenerationUiState
    data class Error(val message: String) : ImageGenerationUiState
}

sealed interface ImageGenerationEvent {
    data object SentToChat : ImageGenerationEvent
    data object SavedToGallery : ImageGenerationEvent
}

@HiltViewModel
class ImageGenerationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val messageRepository: MessageRepository,
    private val personaImageRepository: PersonaImageRepository,
    private val chatImageRepository: ChatImageRepository,
    private val universeImageRepository: UniverseImageRepository,
    private val encryptedImageStore: EncryptedImageStore,
    private val generateImageUseCase: GenerateImageUseCase,
    private val ensurePersonaVisualSheetUseCase: EnsurePersonaVisualSheetUseCase,
    private val describeSceneForImageUseCase: DescribeSceneForImageUseCase,
    private val softenImagePromptUseCase: SoftenImagePromptUseCase,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val npcRepository: NpcRepository,
    private val backendClient: KitsuneBackendClient
) : ViewModel() {

    /** True if the user's next generated image is free (first-time perk) — lets the cost hint
     * say so instead of showing a cost that won't actually be charged. */
    val firstImageFree: StateFlow<Boolean> = backendClient.firstImageFree

    /** Tarif annoncé par le serveur, pour ne plus l'afficher en dur (voir `KitsuneBackendClient`). */
    val imageCostCredits: StateFlow<Int> = backendClient.imageCostCredits

    /** Tarif du niveau HD, annoncé par le serveur (voir `KitsuneBackendClient`). */
    val imageHdCostCredits: StateFlow<Int> = backendClient.imageHdCostCredits

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    val styleOptions: List<PersonaTemplate> = PersonaTemplates.all

    /** Personas in the scene — exactly one for a regular chat, any number for an ensemble chat.
     * Only personas have visual sheets/maturity tags, hence the separate list from [castNpcs]. */
    private var castPersonas: List<PersonaEntity> = emptyList()
    private var castNpcs: List<NpcEntity> = emptyList()

    /** Only ever loaded when there's a single persona and no NPCs. */
    private var avatarBytes: ByteArray? = null

    /** Set only for an ensemble/universe chat — used to also file a kept image under the universe's
     * own gallery (group scenes with no single persona to attribute to). */
    private var universeId: String? = null

    private val _uiState = MutableStateFlow<ImageGenerationUiState>(ImageGenerationUiState.Loading)
    val uiState: StateFlow<ImageGenerationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ImageGenerationEvent>()
    val events: SharedFlow<ImageGenerationEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val chat = chatRepository.getById(chatId)
            if (chat == null) {
                _uiState.value = ImageGenerationUiState.Error("Conversation introuvable")
                return@launch
            }

            val chatPersonaId = chat.personaId
            if (chatPersonaId != null) {
                val persona = personaRepository.getById(chatPersonaId)
                if (persona == null) {
                    _uiState.value = ImageGenerationUiState.Error("Persona introuvable")
                    return@launch
                }
                castPersonas = listOf(persona)
                avatarBytes = persona.avatarImageId?.let { withContext(Dispatchers.IO) { encryptedImageStore.load(it) } }
            } else {
                val participants = chatParticipantRepository.getByChat(chatId)
                castPersonas = participants.filter { it.participantType == ParticipantType.PERSONA }
                    .mapNotNull { personaRepository.getById(it.participantId) }
                castNpcs = participants.filter { it.participantType == ParticipantType.NPC }
                    .mapNotNull { npcRepository.getById(it.participantId) }
                if (castPersonas.isEmpty() && castNpcs.isEmpty()) {
                    _uiState.value = ImageGenerationUiState.Error("Aucun personnage trouvé dans cette scène")
                    return@launch
                }
                universeId = chat.universeId
            }

            val isSinglePersonaChat = castPersonas.size == 1 && castNpcs.isEmpty()
            val names = (castPersonas.map { it.name } + castNpcs.map { it.name }).joinToString(", ")
            _uiState.value = ImageGenerationUiState.Ready(
                characterNames = names,
                showAvatarReferenceOption = isSinglePersonaChat,
                galleryTargets = castPersonas.map { GalleryTarget(it.id, it.name) },
                useAvatarReference = isSinglePersonaChat
            )
        }
    }

    fun updateDescription(value: String) = updateReady { it.copy(description = value) }

    fun selectStyle(style: PersonaTemplate?) = updateReady { it.copy(selectedStyle = style) }

    fun selectAspectRatio(ratio: String?) = updateReady { it.copy(selectedAspectRatio = ratio) }

    fun setUseAvatarReference(value: Boolean) = updateReady { it.copy(useAvatarReference = value) }

    fun setUseHdQuality(value: Boolean) = updateReady { it.copy(useHdQuality = value) }

    fun dismissError() = updateReady { it.copy(error = null) }

    fun generate() = doGenerate()

    fun regenerate() = doGenerate()

    /** Auto-fills [ImageGenerationUiState.Ready.description] from the tail end of the conversation,
     * so the user doesn't have to type the current scene out by hand — they can still edit the
     * result before generating. */
    fun describeCurrentScene() {
        val state = _uiState.value as? ImageGenerationUiState.Ready ?: return
        if (castPersonas.isEmpty() && castNpcs.isEmpty()) return
        if (state.isDescribingScene || state.isGenerating) return

        viewModelScope.launch {
            updateReady { it.copy(isDescribingScene = true, error = null) }

            val recentTurns = messageRepository.getRecent(chatId, SCENE_CONTEXT_MESSAGE_COUNT)
                .asReversed()
                .filter { it.role != MessageRole.SYSTEM && it.role != MessageRole.STYLE_DIRECTIVE }
                .map {
                    ChatTurn(
                        role = if (it.role == MessageRole.USER) ChatMessageDto.ROLE_USER else ChatMessageDto.ROLE_ASSISTANT,
                        content = it.content
                    )
                }
            val characterContext = buildCastImageContext()
            val allowMature = computeAllowMature()

            describeSceneForImageUseCase(recentTurns, characterContext, allowMatureContent = allowMature)
                .onSuccess { description ->
                    updateReady { it.copy(isDescribingScene = false, description = description) }
                }
                .onFailure { e ->
                    updateReady { it.copy(isDescribingScene = false, error = NetworkErrorMessages.forUser(e, "Erreur de description de la scène")) }
                }
        }
    }

    /** Transparently backfills each cast persona's structured visual sheet on first use, same as
     * the in-chat/avatar generation flows (FEATURES.md section 5), then joins every character
     * (personas via their visual sheet, NPCs via plain name+role+description) into one context
     * string covering the whole scene. */
    private suspend fun buildCastImageContext(): String {
        val updatedPersonas = castPersonas.map { persona ->
            val visualSheet = ensurePersonaVisualSheetUseCase(
                name = persona.name,
                shortDescription = persona.shortDescription,
                personality = persona.personality,
                existingVisualSheetJson = persona.visualSheetJson
            )
            if (visualSheet.wasGenerated) {
                persona.copy(visualSheetJson = visualSheet.visualSheetJson, updatedAt = System.currentTimeMillis()).also {
                    personaRepository.upsert(it)
                }
            } else {
                persona
            }
        }
        castPersonas = updatedPersonas
        val personaContexts = updatedPersonas.map { buildPersonaImageContext(it.name, it.shortDescription, it.visualSheetJson) }
        val npcContexts = castNpcs.map { npc ->
            val appearance = npc.physicalDescription?.takeIf { it.isNotBlank() }?.let { ". Apparence : $it" } ?: ""
            "${npc.name} (${npc.role}): ${npc.description}$appearance"
        }
        return (personaContexts + npcContexts).joinToString("\n\n")
    }

    private fun computeAllowMature(): Boolean =
        castPersonas.any { MaturityTag.NSFW in it.maturityTags || MaturityTag.DARK in it.maturityTags }

    private fun doGenerate() {
        val state = _uiState.value as? ImageGenerationUiState.Ready ?: return
        if (castPersonas.isEmpty() && castNpcs.isEmpty()) return
        if (state.isGenerating || state.description.isBlank()) return

        viewModelScope.launch {
            updateReady { it.copy(isGenerating = true, error = null, wasRefused = false) }

            val characterContext = buildCastImageContext()
            val effectiveDescription = state.selectedStyle?.let { "${state.description}\n\nStyle: ${it.styleHint}" } ?: state.description
            val allowMature = computeAllowMature()
            val reference = if (state.useAvatarReference) avatarBytes else null

            generateImageUseCase(
                characterContext = characterContext,
                description = effectiveDescription,
                referenceImage = reference,
                allowMatureContent = allowMature,
                aspectRatio = state.selectedAspectRatio,
                imageQuality = if (state.useHdQuality) "HD" else "STANDARD"
            ).onSuccess { images ->
                updateReady { it.copy(isGenerating = false, generatedImage = images.firstOrNull(), wasSfwFallback = false) }
            }.onFailure { e ->
                val refused = e is ImageGenerationRefusedException
                val message = if (refused) {
                    "Cette image a été refusée par le filtre de contenu du modèle. Vous pouvez essayer une version suggestive à la place."
                } else {
                    NetworkErrorMessages.forUser(e, "Erreur de génération d'image")
                }
                updateReady { it.copy(isGenerating = false, error = message, wasRefused = refused) }
            }
        }
    }

    /**
     * Fallback for a refusal (see [ImageGenerationUiState.Ready.wasRefused]): rewrites the current
     * description into an SFW-but-suggestive version via [SoftenImagePromptUseCase], then retries
     * generation with the *mature* system prompt variant turned off — the softened wording is what's
     * meant to satisfy the model's content policy this time, not the system prompt.
     */
    fun generateSfwFallback() {
        val state = _uiState.value as? ImageGenerationUiState.Ready ?: return
        if (castPersonas.isEmpty() && castNpcs.isEmpty()) return
        if (state.isGenerating || state.description.isBlank()) return

        viewModelScope.launch {
            updateReady { it.copy(isGenerating = true, error = null) }

            val characterContext = buildCastImageContext()
            val effectiveDescription = state.selectedStyle?.let { "${state.description}\n\nStyle: ${it.styleHint}" } ?: state.description

            softenImagePromptUseCase(effectiveDescription, characterContext)
                .onSuccess { softenedDescription ->
                    val reference = if (state.useAvatarReference) avatarBytes else null
                    generateImageUseCase(
                        characterContext = characterContext,
                        description = softenedDescription,
                        referenceImage = reference,
                        allowMatureContent = false,
                        aspectRatio = state.selectedAspectRatio,
                        // Le repli SFW garde le niveau choisi : l'utilisateur a déjà été débité au
                        // tarif correspondant, le rendu ne doit pas être silencieusement dégradé.
                        imageQuality = if (state.useHdQuality) "HD" else "STANDARD"
                    ).onSuccess { images ->
                        updateReady { it.copy(isGenerating = false, generatedImage = images.firstOrNull(), wasSfwFallback = true, wasRefused = false) }
                    }.onFailure { e ->
                        updateReady { it.copy(isGenerating = false, error = "Même la version suggestive a été refusée : ${NetworkErrorMessages.forUser(e)}") }
                    }
                }
                .onFailure { e ->
                    updateReady { it.copy(isGenerating = false, error = NetworkErrorMessages.forUser(e, "Erreur lors de l'adoucissement du prompt")) }
                }
        }
    }

    /** Inserts the currently previewed image as a new assistant message in the conversation. */
    fun sendToChat() {
        val state = _uiState.value as? ImageGenerationUiState.Ready ?: return
        val bytes = state.generatedImage ?: return

        viewModelScope.launch {
            val imageId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
            messageRepository.upsert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    role = MessageRole.ASSISTANT,
                    content = state.description,
                    imageAttachmentPath = imageId,
                    tokenCount = null,
                    createdAt = System.currentTimeMillis()
                )
            )
            chatRepository.getById(chatId)?.let { chat ->
                chatRepository.upsert(chat.copy(updatedAt = System.currentTimeMillis()))
            }
            fileIntoChatAndUniverseGalleries(bytes, state.description)
            _events.emit(ImageGenerationEvent.SentToChat)
        }
    }

    /**
     * Saves the currently previewed image to [targetPersonaId]'s gallery instead of (or in addition
     * to) the chat. [targetPersonaId] must be one of [ImageGenerationUiState.Ready.galleryTargets] —
     * for a regular single-persona chat the UI passes that one persona without asking; for an
     * ensemble chat with several personas in the cast, the UI lets the user pick which one first.
     */
    fun saveToGallery(targetPersonaId: String) {
        val state = _uiState.value as? ImageGenerationUiState.Ready ?: return
        val bytes = state.generatedImage ?: return
        if (state.galleryTargets.none { it.personaId == targetPersonaId }) return

        viewModelScope.launch {
            val imageId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
            personaImageRepository.upsert(
                PersonaImageEntity(
                    id = UUID.randomUUID().toString(),
                    personaId = targetPersonaId,
                    imageStoreId = imageId,
                    description = state.description,
                    createdAt = System.currentTimeMillis()
                )
            )
            fileIntoChatAndUniverseGalleries(bytes, state.description)
            _events.emit(ImageGenerationEvent.SavedToGallery)
        }
    }

    /**
     * Every image the user actually keeps (sent to chat, or saved to a persona's gallery) is also
     * filed into this chat's own gallery, and into the parent universe's gallery for an ensemble
     * chat (group scenes with no single persona to attribute to) — so nothing generated is lost
     * even if the user never explicitly picks a persona to save it under.
     *
     * Re-saves [bytes] under a fresh `EncryptedImageStore` id per destination rather than sharing
     * one id across tables: gallery image deletion (e.g. `PersonaDetailViewModel`) deletes the
     * underlying encrypted blob too, so a shared id would let deleting one gallery entry silently
     * break the image in every other gallery that pointed at the same id.
     */
    private suspend fun fileIntoChatAndUniverseGalleries(bytes: ByteArray, description: String) {
        val chatImageId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
        chatImageRepository.upsert(
            ChatImageEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                imageStoreId = chatImageId,
                description = description,
                createdAt = System.currentTimeMillis()
            )
        )
        universeId?.let { universe ->
            val universeImageId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
            universeImageRepository.upsert(
                UniverseImageEntity(
                    id = UUID.randomUUID().toString(),
                    universeId = universe,
                    imageStoreId = universeImageId,
                    description = description,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun updateReady(transform: (ImageGenerationUiState.Ready) -> ImageGenerationUiState.Ready) {
        (_uiState.value as? ImageGenerationUiState.Ready)?.let { _uiState.value = transform(it) }
    }
}
