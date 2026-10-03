package com.kitsune.feature.universe.create

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.background.GenerationScheduler
import com.kitsune.core.background.UniverseGenerationRequest
import com.kitsune.core.background.parseUniverseResult
import com.kitsune.core.common.inspiration.InspirationDraftHolder
import com.kitsune.core.data.local.entities.FactionEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.data.local.entities.LocationEntity
import com.kitsune.core.data.local.entities.NpcEntity
import com.kitsune.core.data.local.entities.UniverseEntity
import com.kitsune.core.data.repository.FactionRepository
import com.kitsune.core.data.repository.LocationRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.UniverseRepository
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.core.network.repository.FactionDraft
import com.kitsune.core.network.repository.GenerateWorldElementUseCase
import com.kitsune.core.network.repository.LocationDraft
import com.kitsune.core.network.repository.NpcDraft
import com.kitsune.core.network.repository.UniverseBundleDraft
import com.kitsune.core.common.style.MAX_NAME_LENGTH
import com.kitsune.core.network.repository.UniverseDraft
import com.kitsune.feature.universe.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Proposal counts offered to the user — "1 ou 3 ou 5" as requested. */
val UNIVERSE_PROPOSAL_COUNT_OPTIONS = listOf(1, 3, 5)

sealed interface UniverseCreationUiState {
    /** Manual, no-AI form (unchanged from before this feature) — also where the AI description and
     * proposal count are entered before switching into the multi-proposal flow below. */
    data class Form(
        val name: String = "",
        val description: String = "",
        val genre: String = "",
        val visualStyle: String = "",
        val aiDescription: String = "",
        val proposalCount: Int = 1,
        val tags: List<String> = emptyList(),
        val error: String? = null,
        val isSaving: Boolean = false
    ) : UniverseCreationUiState

    data class Generating(val count: Int) : UniverseCreationUiState

    data class Scheduled(val jobId: String, val description: String) : UniverseCreationUiState

    data class GenerationError(val message: String) : UniverseCreationUiState

    /** Browsing [proposals] one at a time ("comme des fiches") — [savedIndices] tracks which ones
     * have already been saved as real universes (more than one can be kept), both to show the user
     * which cards are already done and to guard against saving the same card twice. */
    data class Browsing(
        val proposals: List<UniverseBundleDraft>,
        val currentIndex: Int,
        val savedIndices: Set<Int> = emptySet()
    ) : UniverseCreationUiState
}

@HiltViewModel
class UniverseCreationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val universeRepository: UniverseRepository,
    private val factionRepository: FactionRepository,
    private val locationRepository: LocationRepository,
    private val npcRepository: NpcRepository,
    private val generateWorldElementUseCase: GenerateWorldElementUseCase,
    private val generationScheduler: GenerationScheduler,
    private val generationJobRepository: GenerationJobRepository
) : ViewModel() {

    /** Set when reviewing an asynchronously-generated universe draft from `GenerationJobEntity`. */
    private val draftJobId: String? = savedStateHandle["draftJobId"]

    private val _state = MutableStateFlow<UniverseCreationUiState>(UniverseCreationUiState.Form())
    val state: StateFlow<UniverseCreationUiState> = _state.asStateFlow()

    init {
        // Only set when this screen was just navigated to from the "Je ne sais pas quoi créer"
        // wizard — every other entry path leaves this null.
        InspirationDraftHolder.consume()?.let { description ->
            _state.value = UniverseCreationUiState.Form(aiDescription = description)
        }

        draftJobId?.let { jobId ->
            viewModelScope.launch {
                _state.value = UniverseCreationUiState.Generating(1)
                generationJobRepository.observeById(jobId).collect { job ->
                    when (job?.state) {
                        GenerationJobState.SUCCEEDED -> {
                            val result = job.parseUniverseResult()
                            val proposals = result?.proposals?.map { it.toBundleDraft() }.orEmpty()
                            _state.value = if (proposals.isEmpty()) {
                                UniverseCreationUiState.GenerationError(context.getString(R.string.universe_create_error_empty_result))
                            } else {
                                UniverseCreationUiState.Browsing(proposals = proposals, currentIndex = 0)
                            }
                        }
                        GenerationJobState.FAILED -> {
                            _state.value = UniverseCreationUiState.GenerationError(
                                job.errorMessage ?: context.getString(R.string.universe_create_error_generation_failed)
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

    fun updateName(value: String) = updateForm { it.copy(name = value.take(MAX_NAME_LENGTH), error = null) }
    fun updateDescription(value: String) = updateForm { it.copy(description = value) }
    fun updateGenre(value: String) = updateForm { it.copy(genre = value) }
    fun updateVisualStyle(value: String) = updateForm { it.copy(visualStyle = value) }
    fun updateAiDescription(value: String) = updateForm { it.copy(aiDescription = value) }
    fun updateProposalCount(count: Int) = updateForm { it.copy(proposalCount = count) }

    fun addTag(tag: String) {
        val cleaned = tag.trim().lowercase().replace(",", "")
        if (cleaned.isBlank()) return
        updateForm { if (cleaned in it.tags) it else it.copy(tags = it.tags + cleaned) }
    }

    fun removeTag(tag: String) = updateForm { it.copy(tags = it.tags - tag) }

    /** Manual path, no AI involved — fill the fields yourself and save directly, exactly as before
     * this feature existed. */
    fun save(onSaved: (universeId: String) -> Unit) {
        val current = _state.value as? UniverseCreationUiState.Form ?: return
        if (current.name.isBlank()) {
            _state.value = current.copy(error = context.getString(R.string.universe_create_error_name_required))
            return
        }

        viewModelScope.launch {

            _state.value = current.copy(isSaving = true, error = null)
            val draft = UniverseDraft(name = current.name, description = current.description, genre = current.genre, visualStyle = current.visualStyle, tags = current.tags)
            val id = persistUniverse(draft, emptyList(), emptyList(), emptyList())
            onSaved(id)
        }
    }

    /**
     * Schedules a background WorkManager job that generates [UniverseCreationUiState.Form.proposalCount]
     * complete universe proposals. The user can leave the app; the worker persists the result in
     * `GenerationJobEntity` and posts a notification when finished. The review step happens from the
     * universe list (or from the notification), not from this screen.
     */
    fun generateProposals() {
        val form = _state.value as? UniverseCreationUiState.Form ?: return
        val count = form.proposalCount

        viewModelScope.launch {

            _state.value = UniverseCreationUiState.Generating(count)
            try {
                val jobId = generationScheduler.scheduleUniverseGeneration(
                    UniverseGenerationRequest(
                        description = form.aiDescription,
                        proposalCount = count
                    )
                )
                _state.value = UniverseCreationUiState.Scheduled(jobId = jobId, description = form.aiDescription)
                observeJob(jobId)
            } catch (e: Exception) {
                _state.value = UniverseCreationUiState.GenerationError(
                    NetworkErrorMessages.forUser(e) ?: context.getString(R.string.universe_create_error_scheduling_failed)
                )
            }
        }
    }

    private fun observeJob(jobId: String) {
        android.util.Log.i("UniverseVM", "observeJob: jobId=$jobId")
        viewModelScope.launch {
            generationJobRepository.observeById(jobId).collect { job ->
                android.util.Log.i("UniverseVM", "observeJob: job=${job?.id}, state=${job?.state}, error=${job?.errorMessage}, resultLen=${job?.resultJson?.length}")
                when (job?.state) {
                    GenerationJobState.SUCCEEDED -> {
                        val result = job.parseUniverseResult()
                        val proposals = result?.proposals?.map { it.toBundleDraft() }.orEmpty()
                        _state.value = if (proposals.isEmpty()) {
                            UniverseCreationUiState.GenerationError(context.getString(R.string.universe_create_error_empty_result))
                        } else {
                            UniverseCreationUiState.Browsing(proposals = proposals, currentIndex = 0)
                        }
                    }
                    GenerationJobState.FAILED -> {
                        _state.value = UniverseCreationUiState.GenerationError(
                            job.errorMessage ?: context.getString(R.string.universe_create_error_generation_failed)
                        )
                    }
                    GenerationJobState.PENDING,
                    GenerationJobState.RUNNING,
                    null -> { /* keep waiting */ }
                }
            }
        }
    }

    fun retryFromForm() {
        _state.value = UniverseCreationUiState.Form()
    }

    fun showNextProposal() = updateBrowsing { it.copy(currentIndex = (it.currentIndex + 1).coerceAtMost(it.proposals.size - 1)) }
    fun showPreviousProposal() = updateBrowsing { it.copy(currentIndex = (it.currentIndex - 1).coerceAtLeast(0)) }

    /**
     * Saves the currently-shown proposal as a real, independent universe immediately — no separate
     * review step, unlike persona creation: a universe carries no age-verification lock (FEATURES.md
     * section 3), so there is no safety reason to force a review before saving here. The user can
     * keep browsing afterwards and save others too ("ou plusieurs").
     */
    fun selectCurrentProposal() {
        val browsing = _state.value as? UniverseCreationUiState.Browsing ?: return
        if (browsing.currentIndex in browsing.savedIndices) return
        val bundle = browsing.proposals.getOrNull(browsing.currentIndex) ?: return

        viewModelScope.launch {

            persistUniverse(bundle.universe, bundle.factions, bundle.locations, bundle.npcs)
            (_state.value as? UniverseCreationUiState.Browsing)?.let {
                _state.value = it.copy(savedIndices = it.savedIndices + browsing.currentIndex)
            }
            // Delete the draft job so the card disappears from the universe list
            draftJobId?.let { jobId ->
                runCatching { generationJobRepository.deleteById(jobId) }
            }
        }
    }

    private suspend fun persistUniverse(
        universe: UniverseDraft,
        factions: List<FactionDraft>,
        locations: List<LocationDraft>,
        npcs: List<NpcDraft>
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        universeRepository.insert(
            UniverseEntity(
                id = id,
                name = universe.name,
                description = universe.description,
                genre = universe.genre,
                visualStyle = universe.visualStyle.takeIf { it.isNotBlank() },
                tags = universe.tags,
                createdAt = now,
                updatedAt = now
            )
        )
        factions.forEach { faction ->
            factionRepository.insert(
                FactionEntity(
                    id = UUID.randomUUID().toString(),
                    universeId = id,
                    name = faction.name,
                    description = faction.description,
                    type = faction.type,
                    alignment = faction.alignment.takeIf { it.isNotBlank() },
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        locations.forEach { location ->
            locationRepository.insert(
                LocationEntity(
                    id = UUID.randomUUID().toString(),
                    universeId = id,
                    name = location.name,
                    description = location.description,
                    type = location.type,
                    parentLocationId = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        npcs.forEach { npc ->
            npcRepository.insert(
                NpcEntity(
                    id = UUID.randomUUID().toString(),
                    universeId = id,
                    name = npc.name,
                    description = npc.description,
                    personality = npc.personality,
                    role = npc.role,
                    factionId = null,
                    locationId = null,
                    age = null,
                    physicalDescription = npc.physicalDescription.takeIf { it.isNotBlank() },
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return id
    }

    private fun updateForm(transform: (UniverseCreationUiState.Form) -> UniverseCreationUiState.Form) {
        (_state.value as? UniverseCreationUiState.Form)?.let { _state.value = transform(it) }
    }

    private fun updateBrowsing(transform: (UniverseCreationUiState.Browsing) -> UniverseCreationUiState.Browsing) {
        (_state.value as? UniverseCreationUiState.Browsing)?.let { _state.value = transform(it) }
    }
}

private fun com.kitsune.core.background.UniverseBundleResult.toBundleDraft(): UniverseBundleDraft =
    UniverseBundleDraft(
        universe = UniverseDraft(
            name = universeName,
            description = universeDescription,
            genre = universeGenre,
            visualStyle = visualStyle
        ),
        factions = factions.map { FactionDraft(it.name, it.description, it.type, it.alignment) },
        locations = locations.map { LocationDraft(it.name, it.description, it.type) },
        npcs = npcs.map { NpcDraft(it.name, it.description, it.personality, it.role, it.physicalDescription) }
    )
