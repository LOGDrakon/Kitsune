package com.kitsune.feature.persona.imagegen

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.common.style.PersonaTemplate
import com.kitsune.core.common.style.PersonaTemplates
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.PersonaImageEntity
import com.kitsune.core.data.repository.PersonaImageRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.core.network.repository.ASPECT_RATIO_OPTIONS
import com.kitsune.core.network.repository.GenerateImageUseCase
import com.kitsune.core.network.repository.ImageGenerationRefusedException
import com.kitsune.core.network.repository.SoftenImagePromptUseCase
import com.kitsune.core.network.visualsheet.EnsurePersonaVisualSheetUseCase
import com.kitsune.core.network.visualsheet.buildPersonaImageContext
import com.kitsune.core.security.storage.EncryptedImageStore
import com.kitsune.feature.persona.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

sealed interface PersonaImageGenerationUiState {
    data object Loading : PersonaImageGenerationUiState
    data class Ready(
        val personaName: String,
        val description: String = "",
        val selectedStyle: PersonaTemplate? = null,
        val selectedAspectRatio: String? = null,
        val useAvatarReference: Boolean = true,
        val hasAvatar: Boolean = false,
        val isGenerating: Boolean = false,
        val generatedImage: ByteArray? = null,
        /** True right after a generation attempt was declined by the image model's own content
         * policy (not a network/other error) — the UI offers the SFW fallback only in this case. */
        val wasRefused: Boolean = false,
        /** True when [generatedImage] was produced via [SoftenImagePromptUseCase] after an initial
         * refusal, so the UI can flag that it may not exactly match the requested scene. */
        val wasSfwFallback: Boolean = false,
        val error: String? = null
    ) : PersonaImageGenerationUiState
    data class Error(val message: String) : PersonaImageGenerationUiState
}

/** Emitted once the generated image has been filed into the persona's gallery, so the screen can
 * navigate back. */
data object PersonaImageSavedEvent

/**
 * Persona-detail counterpart of `feature:chat`'s `ImageGenerationScreen` — same visual-style/
 * aspect-ratio controls and refusal/SFW-fallback flow, reused here rather than shared directly
 * (feature modules can't depend on each other, only on `core:*` — see `AspectRatioOption`/
 * `PersonaTemplate`, both already `core`). No "generate from current scene" here: this screen has
 * no scene, just a persona sheet, so its context always comes from the persona's own visual sheet.
 * Always saves straight to this persona's own gallery — no "send to chat" (there is none) and no
 * gallery-target picker (there's only ever one target, this persona).
 */
@HiltViewModel
class PersonaImageGenerationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val personaRepository: PersonaRepository,
    private val personaImageRepository: PersonaImageRepository,
    private val encryptedImageStore: EncryptedImageStore,
    private val generateImageUseCase: GenerateImageUseCase,
    private val ensurePersonaVisualSheetUseCase: EnsurePersonaVisualSheetUseCase,
    private val softenImagePromptUseCase: SoftenImagePromptUseCase
) : ViewModel() {

    private val personaId: String = checkNotNull(savedStateHandle["personaId"])

    val styleOptions: List<PersonaTemplate> = PersonaTemplates.all
    val aspectRatioOptions = ASPECT_RATIO_OPTIONS

    private var persona: PersonaEntity? = null
    private var avatarBytes: ByteArray? = null

    private val _uiState = MutableStateFlow<PersonaImageGenerationUiState>(PersonaImageGenerationUiState.Loading)
    val uiState: StateFlow<PersonaImageGenerationUiState> = _uiState.asStateFlow()

    private val _savedEvent = MutableSharedFlow<PersonaImageSavedEvent>()
    val savedEvent: SharedFlow<PersonaImageSavedEvent> = _savedEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            val loaded = personaRepository.getById(personaId)
            if (loaded == null) {
                _uiState.value = PersonaImageGenerationUiState.Error(appContext.getString(R.string.persona_detail_error_not_found))
                return@launch
            }
            persona = loaded
            avatarBytes = loaded.avatarImageId?.let { withContext(Dispatchers.IO) { encryptedImageStore.load(it) } }
            _uiState.value = PersonaImageGenerationUiState.Ready(
                personaName = loaded.name,
                hasAvatar = avatarBytes != null,
                useAvatarReference = avatarBytes != null
            )
        }
    }

    fun updateDescription(value: String) = updateReady { it.copy(description = value) }
    fun selectStyle(style: PersonaTemplate?) = updateReady { it.copy(selectedStyle = style) }
    fun selectAspectRatio(ratio: String?) = updateReady { it.copy(selectedAspectRatio = ratio) }
    fun setUseAvatarReference(value: Boolean) = updateReady { it.copy(useAvatarReference = value) }
    fun dismissError() = updateReady { it.copy(error = null) }

    fun generate() = doGenerate()
    fun regenerate() = doGenerate()

    /** Backfills the persona's structured visual sheet on first use (same as every other image
     * entry point, FEATURES.md section 5), persisting it if it was just generated. */
    private suspend fun ensureUpToDatePersona(): PersonaEntity {
        val current = persona ?: error("persona not loaded")
        val visualSheet = ensurePersonaVisualSheetUseCase(
            name = current.name,
            shortDescription = current.shortDescription,
            personality = current.personality,
            existingVisualSheetJson = current.visualSheetJson
        )
        if (!visualSheet.wasGenerated) return current
        val updated = current.copy(visualSheetJson = visualSheet.visualSheetJson, updatedAt = System.currentTimeMillis())
        personaRepository.upsert(updated)
        persona = updated
        return updated
    }

    private fun doGenerate() {
        val state = _uiState.value as? PersonaImageGenerationUiState.Ready ?: return
        if (state.isGenerating || state.description.isBlank()) return

        viewModelScope.launch {
            updateReady { it.copy(isGenerating = true, error = null, wasRefused = false) }

            val current = ensureUpToDatePersona()
            val characterContext = buildPersonaImageContext(current.name, current.shortDescription, current.visualSheetJson)
            val effectiveDescription = state.selectedStyle?.let { "${state.description}\n\nStyle: ${it.styleHint}" } ?: state.description
            val allowMature = MaturityTag.NSFW in current.maturityTags || MaturityTag.DARK in current.maturityTags
            val reference = if (state.useAvatarReference) avatarBytes else null

            generateImageUseCase(
                characterContext = characterContext,
                description = effectiveDescription,
                referenceImage = reference,
                allowMatureContent = allowMature,
                aspectRatio = state.selectedAspectRatio
            ).onSuccess { images ->
                updateReady { it.copy(isGenerating = false, generatedImage = images.firstOrNull(), wasSfwFallback = false) }
            }.onFailure { e ->
                val refused = e is ImageGenerationRefusedException
                val message = if (refused) {
                    appContext.getString(R.string.persona_image_gen_refused_message)
                } else {
                    NetworkErrorMessages.forUser(e, appContext.getString(R.string.persona_detail_error_image_generation_fallback))
                }
                updateReady { it.copy(isGenerating = false, error = message, wasRefused = refused) }
            }
        }
    }

    /** Same refusal fallback as `feature:chat`'s image generation: rewrite the description into an
     * SFW-but-suggestive version, retry with the mature system-prompt variant turned off. */
    fun generateSfwFallback() {
        val state = _uiState.value as? PersonaImageGenerationUiState.Ready ?: return
        if (state.isGenerating || state.description.isBlank()) return

        viewModelScope.launch {
            updateReady { it.copy(isGenerating = true, error = null) }

            val current = ensureUpToDatePersona()
            val characterContext = buildPersonaImageContext(current.name, current.shortDescription, current.visualSheetJson)
            val effectiveDescription = state.selectedStyle?.let { "${state.description}\n\nStyle: ${it.styleHint}" } ?: state.description

            softenImagePromptUseCase(effectiveDescription, characterContext)
                .onSuccess { softenedDescription ->
                    val reference = if (state.useAvatarReference) avatarBytes else null
                    generateImageUseCase(
                        characterContext = characterContext,
                        description = softenedDescription,
                        referenceImage = reference,
                        allowMatureContent = false,
                        aspectRatio = state.selectedAspectRatio
                    ).onSuccess { images ->
                        updateReady { it.copy(isGenerating = false, generatedImage = images.firstOrNull(), wasSfwFallback = true, wasRefused = false) }
                    }.onFailure { e ->
                        updateReady {
                            it.copy(
                                isGenerating = false,
                                error = appContext.getString(R.string.persona_image_gen_sfw_also_refused, NetworkErrorMessages.forUser(e) ?: "")
                            )
                        }
                    }
                }
                .onFailure { e ->
                    updateReady { it.copy(isGenerating = false, error = NetworkErrorMessages.forUser(e, appContext.getString(R.string.persona_image_gen_soften_error))) }
                }
        }
    }

    /** Always files into this persona's own gallery — unlike the in-chat entry point, there is no
     * chat to send to and no other persona this could belong to. */
    fun saveToGallery() {
        val state = _uiState.value as? PersonaImageGenerationUiState.Ready ?: return
        val bytes = state.generatedImage ?: return

        viewModelScope.launch {
            val imageId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
            personaImageRepository.upsert(
                PersonaImageEntity(
                    id = UUID.randomUUID().toString(),
                    personaId = personaId,
                    imageStoreId = imageId,
                    description = state.description,
                    createdAt = System.currentTimeMillis()
                )
            )
            _savedEvent.emit(PersonaImageSavedEvent)
        }
    }

    private fun updateReady(transform: (PersonaImageGenerationUiState.Ready) -> PersonaImageGenerationUiState.Ready) {
        (_uiState.value as? PersonaImageGenerationUiState.Ready)?.let { _uiState.value = transform(it) }
    }
}
