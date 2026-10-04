package com.kitsune.feature.persona.create

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.common.style.PersonaTemplate
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.repository.ChatParticipantRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.background.GenerationScheduler
import com.kitsune.core.background.PersonaGenerationRequest
import com.kitsune.core.background.parsePersonaResult
import com.kitsune.core.common.inspiration.InspirationDraftHolder
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.core.network.visualsheet.PersonaVisualSheet
import com.kitsune.core.network.persona.GenerateQuickPersonaUseCase
import com.kitsune.core.network.persona.PersonaDraft
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.profile.UserProfileStore
import com.kitsune.feature.persona.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val MINIMUM_PERSONA_AGE = 18

/** Proposal counts offered to the user — "1 ou 3 ou 5" as requested. */
val PERSONA_PROPOSAL_COUNT_OPTIONS = listOf(1, 3, 5)

@HiltViewModel
class PersonaCreationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val generateQuickPersonaUseCase: GenerateQuickPersonaUseCase,
    private val personaRepository: PersonaRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val npcRepository: NpcRepository,
    private val generationScheduler: GenerationScheduler,
    private val generationJobRepository: GenerationJobRepository,
    private val appLanguageManager: AppLanguageManager,
    private val userProfileStore: UserProfileStore
) : ViewModel() {

    /** Set when this persona is created from within a universe (`UniverseDetailScreen` > "Personas jouables"). */
    private val universeId: String? = savedStateHandle["universeId"]

    /** Set when created from an ongoing ensemble chat ("Ajouter un personnage à la scène" >
     * "Nouveau persona", or the "convertir un PNJ en persona" flow) — the newly saved persona is
     * automatically added to this chat's cast, on top of being saved to the universe. */
    private val chatId: String? = savedStateHandle["chatId"]

    /** Set when creating a persona FROM an existing NPC already in a conversation ("convertir en
     * persona") — pre-fills the description step so the user doesn't retype what's already known. */
    private val fromNpcId: String? = savedStateHandle["fromNpcId"]

    /** Set when reviewing an asynchronously-generated persona draft from `GenerationJobEntity`. */
    private val draftJobId: String? = savedStateHandle["draftJobId"]

    private val _state = MutableStateFlow<PersonaCreationUiState>(PersonaCreationUiState.DescribeInput)
    val state: StateFlow<PersonaCreationUiState> = _state.asStateFlow()

    private val _initialDescription = MutableStateFlow("")
    val initialDescription: StateFlow<String> = _initialDescription.asStateFlow()

    init {
        viewModelScope.launch {
            // Gate on the user's very first persona only (not every creation) — count-based, not a
            // set-once sentinel, so it re-applies if the first persona is
            // later deleted and the profile is still blank. Demande explicite : les personas ne
            // doivent pas être créés avant que l'utilisateur ait renseigné sa propre fiche.
            val isFirstPersona = personaRepository.observeAll().first().isEmpty()
            if (isFirstPersona && userProfileStore.get().isBlank) {
                _state.value = PersonaCreationUiState.RequiresProfileSetup
                return@launch
            }

            fromNpcId?.let { id ->
                npcRepository.getById(id)?.let { npc ->
                    _initialDescription.value = buildString {
                        append(npc.name)
                        if (npc.description.isNotBlank()) append(", ${npc.description}")
                        if (npc.personality.isNotBlank()) append(". Personnalité : ${npc.personality}")
                        if (npc.role.isNotBlank()) append(". Rôle : ${npc.role}")
                    }
                }
            }

            // Only set when this screen was just navigated to from the "Je ne sais pas quoi
            // créer" wizard — every other entry path (including fromNpcId above) leaves this null.
            if (fromNpcId == null) {
                InspirationDraftHolder.consume()?.let { _initialDescription.value = it }
            }

            draftJobId?.let { jobId ->
                _state.value = PersonaCreationUiState.Generating(1)
                generationJobRepository.observeById(jobId).collect { job ->
                    when (job?.state) {
                        GenerationJobState.SUCCEEDED -> {
                            val result = job.parsePersonaResult()
                            val proposals = result?.proposals?.map {
                                PersonaDraft(
                                    name = it.name,
                                    description = it.description,
                                    personality = it.personality,
                                    scenario = it.scenario,
                                    firstMessage = it.firstMessage,
                                    exampleDialogues = it.exampleDialogues,
                                    desire = it.desire,
                                    fear = it.fear,
                                    flaw = it.flaw,
                                    moralLine = it.moralLine,
                                    secret = it.secret,
                                    visualSheet = PersonaVisualSheet(
                                        physicalTraits = it.physicalTraits,
                                        artStyle = it.artStyle,
                                        colorPalette = it.colorPalette,
                                        defaultOutfit = it.defaultOutfit
                                    )
                                )
                            }.orEmpty()
                            _state.value = when {
                                proposals.isEmpty() -> PersonaCreationUiState.GenerationError(
                                    appContext.getString(R.string.persona_create_error_empty_result)
                                )
                                proposals.size == 1 -> reviewStateFor(proposals.first())
                                else -> PersonaCreationUiState.Browsing(proposals = proposals, currentIndex = 0)
                            }
                        }
                        GenerationJobState.FAILED -> {
                            _state.value = PersonaCreationUiState.GenerationError(
                                job.errorMessage ?: appContext.getString(R.string.persona_create_error_generation_failed)
                            )
                        }
                        GenerationJobState.PENDING,
                        GenerationJobState.RUNNING,
                        null -> { /* keep waiting */ }
                    }
                }
            }
        }
    }

    /**
     * Schedules a background WorkManager job that generates [count] complete persona proposals.
     * The user can leave the app; the worker persists the result in `GenerationJobEntity` and
     * posts a notification when finished. The review step now happens from the persona list (or
     * from the notification), not from this screen.
     */
    fun generate(description: String, template: PersonaTemplate? = null, count: Int = 1) {
        viewModelScope.launch {
            _state.value = PersonaCreationUiState.Generating(count)
            val promptWithStyle = if (template != null) {
                "$description\n\nStyle: ${template.styleHint}"
            } else {
                description
            }
            try {
                val jobId = generationScheduler.schedulePersonaGeneration(
                    PersonaGenerationRequest(
                        description = promptWithStyle,
                        templateStyleHint = template?.styleHint,
                        proposalCount = count,
                        universeId = universeId,
                        chatId = chatId
                    )
                )
                _state.value = PersonaCreationUiState.Scheduled(jobId = jobId, description = description)
            } catch (e: Exception) {
                _state.value = PersonaCreationUiState.GenerationError(
                    NetworkErrorMessages.forUser(e) ?: appContext.getString(R.string.persona_create_error_schedule_failed)
                )
            }
        }
    }

    fun showNextProposal() = updateBrowsing { it.copy(currentIndex = (it.currentIndex + 1).coerceAtMost(it.proposals.size - 1)) }
    fun showPreviousProposal() = updateBrowsing { it.copy(currentIndex = (it.currentIndex - 1).coerceAtLeast(0)) }

    /** Picks the currently-shown proposal and moves to the age/maturity review step. Guards
     * against reopening a proposal already saved, same as universe creation. */
    fun selectCurrentProposal() {
        val browsing = _state.value as? PersonaCreationUiState.Browsing ?: return
        if (browsing.isPreparingSelection || browsing.currentIndex in browsing.savedIndices) return
        val draft = browsing.proposals.getOrNull(browsing.currentIndex) ?: return

        viewModelScope.launch {
            _state.value = browsing.copy(isPreparingSelection = true)
            _state.value = reviewStateFor(draft, sourceBrowsing = browsing)
        }
    }

    private fun reviewStateFor(draft: PersonaDraft, sourceBrowsing: PersonaCreationUiState.Browsing? = null): PersonaCreationUiState.Review {
        // The visual sheet is generated in the same LLM call as the rest of the character sheet
        // (GenerateQuickPersonaUseCase), so it's already available here — no second wait, no second credit.
        return PersonaCreationUiState.Review(draft = draft, visualSheet = draft.visualSheet, sourceBrowsing = sourceBrowsing)
    }

    fun retryFromDescription() {
        _state.value = PersonaCreationUiState.DescribeInput
    }

    fun updateAge(age: String) {
        withReview { it.copy(age = age, error = null) }
    }

    fun toggleMaturityTag(tag: MaturityTag) {
        withReview { review ->
            val tags = if (tag in review.maturityTags) review.maturityTags - tag else review.maturityTags + tag
            review.copy(maturityTags = tags.ifEmpty { setOf(MaturityTag.SFW) })
        }
    }

    fun addTag(tag: String) {
        val cleaned = tag.trim().lowercase().replace(",", "")
        if (cleaned.isBlank()) return
        withReview { review ->
            if (cleaned in review.tags) review else review.copy(tags = review.tags + cleaned)
        }
    }

    fun removeTag(tag: String) = withReview { it.copy(tags = it.tags - tag) }

    fun updatePhysicalTraits(value: String) = withReview { it.copy(visualSheet = it.visualSheet.copy(physicalTraits = value)) }
    fun updateArtStyle(value: String) = withReview { it.copy(visualSheet = it.visualSheet.copy(artStyle = value)) }
    fun updateColorPalette(value: String) = withReview { it.copy(visualSheet = it.visualSheet.copy(colorPalette = value)) }
    fun updateDefaultOutfit(value: String) = withReview { it.copy(visualSheet = it.visualSheet.copy(defaultOutfit = value)) }

    fun save(onSaved: (personaId: String) -> Unit) {
        val review = _state.value as? PersonaCreationUiState.Review ?: return
        val age = review.age.toIntOrNull()

        // Hard block, not just a warning: personas depicting minors are never allowed
        // (FEATURES.md section 3 — non-negotiable, anti-contournement).
        if (age == null || age < MINIMUM_PERSONA_AGE) {
            _state.value = review.copy(
                error = appContext.getString(R.string.persona_review_error_age_minimum, MINIMUM_PERSONA_AGE)
            )
            return
        }

        viewModelScope.launch {
            _state.value = review.copy(isSaving = true, error = null)
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            personaRepository.upsert(
                PersonaEntity(
                    id = id,
                    universeId = universeId,
                    name = review.draft.name,
                    shortDescription = review.draft.description,
                    personality = review.draft.personality,
                    scenario = review.draft.scenario,
                    firstMessage = review.draft.firstMessage,
                    exampleDialogues = review.draft.exampleDialogues,
                    // Ressorts intimes (2026-08-22), produits par le même appel de génération : ce
                    // sont eux qui donnent au modèle de quoi jouer un refus ou une complication qui
                    // ressemblent au personnage, plutôt que de la prose d'ambiance.
                    desire = review.draft.desire,
                    fear = review.draft.fear,
                    flaw = review.draft.flaw,
                    moralLine = review.draft.moralLine,
                    secret = review.draft.secret,
                    age = age,
                    maturityTags = review.maturityTags.toList(),
                    tags = review.tags,
                    visualSheetJson = review.visualSheet.takeUnless { it.isBlank }?.encode(),
                    contentLanguage = appLanguageManager.getSelectedLanguage().languageTag,
                    createdAt = now,
                    updatedAt = now
                )
            )
            chatId?.let { chatParticipantRepository.addParticipant(it, ParticipantType.PERSONA, id) }
            // Delete the draft job if this was a draft review — the card should not linger
            draftJobId?.let { jobId ->
                runCatching { generationJobRepository.deleteById(jobId) }
            }

            val sourceBrowsing = review.sourceBrowsing
            if (sourceBrowsing != null) {
                // Multi-proposal batch ("comme avec les univers") — return to Browsing instead of
                // exiting, so the user can review and save more proposals from the same batch.
                _state.value = sourceBrowsing.copy(
                    savedIndices = sourceBrowsing.savedIndices + sourceBrowsing.currentIndex,
                    isPreparingSelection = false
                )
            } else {
                onSaved(id)
            }
        }
    }

    private inline fun withReview(transform: (PersonaCreationUiState.Review) -> PersonaCreationUiState.Review) {
        val current = _state.value as? PersonaCreationUiState.Review ?: return
        _state.value = transform(current)
    }

    private inline fun updateBrowsing(transform: (PersonaCreationUiState.Browsing) -> PersonaCreationUiState.Browsing) {
        val current = _state.value as? PersonaCreationUiState.Browsing ?: return
        _state.value = transform(current)
    }
}
