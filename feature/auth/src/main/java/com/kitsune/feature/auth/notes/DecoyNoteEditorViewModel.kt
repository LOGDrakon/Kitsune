package com.kitsune.feature.auth.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.entities.NoteEntity
import com.kitsune.core.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface NoteEditorUiState {
    data object Loading : NoteEditorUiState
    data class Ready(
        val id: String,
        val title: String,
        val body: String,
        val isPinned: Boolean,
        val isNew: Boolean,
        val createdAt: Long
    ) : NoteEditorUiState
}

/**
 * Saves on every edit rather than only on back-navigation — simpler than reliably intercepting
 * every way to leave the screen (back arrow, system back gesture, process death), and just as
 * cheap given the local SQLCipher-backed database. A brand-new note is only ever persisted once it
 * has actual content (title or body non-blank) — backing out of an untouched blank note leaves no
 * trace.
 */
@HiltViewModel
class DecoyNoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val noteId: String? = savedStateHandle["noteId"]

    private val _uiState = MutableStateFlow<NoteEditorUiState>(NoteEditorUiState.Loading)
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = noteId?.let { noteRepository.getById(it) }
            _uiState.value = NoteEditorUiState.Ready(
                id = existing?.id ?: UUID.randomUUID().toString(),
                title = existing?.title.orEmpty(),
                body = existing?.body.orEmpty(),
                isPinned = existing?.isPinned ?: false,
                isNew = existing == null,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        }
    }

    fun save(title: String, body: String, isPinned: Boolean) {
        val state = _uiState.value as? NoteEditorUiState.Ready ?: return
        _uiState.value = state.copy(title = title, body = body, isPinned = isPinned, isNew = false)
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch {
            noteRepository.upsert(
                NoteEntity(
                    id = state.id,
                    title = title,
                    body = body,
                    isPinned = isPinned,
                    createdAt = state.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** No-op for a note that was never actually saved (still blank). */
    fun delete() {
        val state = _uiState.value as? NoteEditorUiState.Ready ?: return
        if (state.isNew) return
        viewModelScope.launch {
            noteRepository.delete(
                NoteEntity(
                    id = state.id,
                    title = "",
                    body = "",
                    createdAt = state.createdAt,
                    updatedAt = state.createdAt
                )
            )
        }
    }
}
