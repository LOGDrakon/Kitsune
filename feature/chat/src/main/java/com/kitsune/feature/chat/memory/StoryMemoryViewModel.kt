package com.kitsune.feature.chat.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StoryMemoryUiState {
    data object Loading : StoryMemoryUiState
    data class Ready(
        val chat: ChatEntity,
        val loreEntries: List<LoreEntryEntity>,
        val editingLoreEntry: LoreEntryEntity? = null
    ) : StoryMemoryUiState
    data class Error(val message: String) : StoryMemoryUiState
}

@HiltViewModel
class StoryMemoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val loreEntryRepository: LoreEntryRepository
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow<StoryMemoryUiState>(StoryMemoryUiState.Loading)
    val uiState: StateFlow<StoryMemoryUiState> = _uiState.asStateFlow()

    init {
        loadMemory()
    }

    private fun loadMemory() {
        viewModelScope.launch {
            try {
                val chat = chatRepository.getById(chatId)
                if (chat == null) {
                    _uiState.value = StoryMemoryUiState.Error("Conversation introuvable")
                    return@launch
                }

                val loreEntries = loreEntryRepository.getByChat(chatId)
                _uiState.value = StoryMemoryUiState.Ready(
                    chat = chat,
                    loreEntries = loreEntries
                )
            } catch (e: Exception) {
                _uiState.value = StoryMemoryUiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun startEditingLoreEntry(entry: LoreEntryEntity) {
        val state = _uiState.value as? StoryMemoryUiState.Ready ?: return
        _uiState.value = state.copy(editingLoreEntry = entry)
    }

    fun cancelEditing() {
        val state = _uiState.value as? StoryMemoryUiState.Ready ?: return
        _uiState.value = state.copy(editingLoreEntry = null)
    }

    fun saveLoreEntry(name: String, summary: String, content: String, occurredAt: String) {
        val state = _uiState.value as? StoryMemoryUiState.Ready ?: return
        val editing = state.editingLoreEntry ?: return

        viewModelScope.launch {
            try {
                val updated = editing.copy(
                    name = name,
                    summary = summary,
                    content = content,
                    occurredAt = occurredAt,
                    updatedAt = System.currentTimeMillis(),
                    version = editing.version + 1
                )
                loreEntryRepository.upsert(updated)
                _uiState.value = state.copy(editingLoreEntry = null)
                loadMemory()
            } catch (e: Exception) {
                _uiState.value = StoryMemoryUiState.Error(e.message ?: "Erreur de sauvegarde")
            }
        }
    }

    fun deleteLoreEntry(entry: LoreEntryEntity) {
        viewModelScope.launch {
            try {
                loreEntryRepository.delete(entry)
                loadMemory()
            } catch (e: Exception) {
                _uiState.value = StoryMemoryUiState.Error(e.message ?: "Erreur de suppression")
            }
        }
    }

    fun dismissError() {
        val state = _uiState.value
        if (state is StoryMemoryUiState.Error) {
            loadMemory()
        }
    }
}
