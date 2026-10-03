package com.kitsune.feature.universe.chatcreate

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.feature.universe.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MINIMUM_CAST_SIZE = 2

data class CastCandidate(
    val type: ParticipantType,
    val id: String,
    val name: String,
    val subtitle: String
)

sealed interface UniverseChatCreationUiState {
    data object Loading : UniverseChatCreationUiState
    data class Ready(
        val candidates: List<CastCandidate>,
        val selectedIds: Set<String> = emptySet(),
        val title: String = "",
        val isSaving: Boolean = false,
        val error: String? = null
    ) : UniverseChatCreationUiState
}

/**
 * Ensemble/universe chat creation (FEATURES.md section 6, requested explicitly by the user): pick
 * a cast of personas and/or NPCs belonging to the universe — the AI plays all of them together in
 * a shared scene, with no single fixed protagonist (unlike a regular persona chat).
 */
@HiltViewModel
class UniverseChatCreationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val personaRepository: PersonaRepository,
    private val npcRepository: NpcRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val universeId: String = checkNotNull(savedStateHandle["universeId"])

    private val _uiState = MutableStateFlow<UniverseChatCreationUiState>(UniverseChatCreationUiState.Loading)
    val uiState: StateFlow<UniverseChatCreationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val personas = personaRepository.observeByUniverse(universeId).first()
                .map { CastCandidate(ParticipantType.PERSONA, it.id, it.name, it.shortDescription) }
            val npcs = npcRepository.getByUniverse(universeId).first()
                .map { CastCandidate(ParticipantType.NPC, it.id, it.name, it.role) }
            _uiState.value = UniverseChatCreationUiState.Ready(candidates = personas + npcs)
        }
    }

    fun toggleSelection(id: String) = updateReady {
        it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id, error = null)
    }

    fun updateTitle(value: String) = updateReady { it.copy(title = value) }

    fun save(onSaved: (chatId: String) -> Unit) {
        val state = _uiState.value as? UniverseChatCreationUiState.Ready ?: return
        if (state.isSaving) return
        if (state.selectedIds.size < MINIMUM_CAST_SIZE) {
            updateReady { it.copy(error = context.getString(R.string.universe_chat_create_error_min_cast_format, MINIMUM_CAST_SIZE)) }
            return
        }

        viewModelScope.launch {
            updateReady { it.copy(isSaving = true, error = null) }
            val cast = state.candidates.filter { it.id in state.selectedIds }.map { it.type to it.id }
            val title = state.title.ifBlank { context.getString(R.string.universe_chat_create_default_title) }
            val chat = chatRepository.createUniverseChat(universeId, title, cast)
            onSaved(chat.id)
        }
    }

    private fun updateReady(transform: (UniverseChatCreationUiState.Ready) -> UniverseChatCreationUiState.Ready) {
        (_uiState.value as? UniverseChatCreationUiState.Ready)?.let { _uiState.value = transform(it) }
    }
}
