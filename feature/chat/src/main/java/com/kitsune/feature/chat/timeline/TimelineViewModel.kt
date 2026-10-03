package com.kitsune.feature.chat.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.entities.KeyMomentEntity
import com.kitsune.core.data.repository.KeyMomentRepository
import com.kitsune.core.memory.timeline.ScriptDoctorUseCase
import com.kitsune.core.security.storage.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val keyMomentRepository: KeyMomentRepository,
    private val scriptDoctorUseCase: ScriptDoctorUseCase,
    secureStorage: SecureStorage
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    val moments: StateFlow<List<KeyMomentEntity>> =
        keyMomentRepository.observeByChat(chatId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Owned via the "Personnalisation" section of the Store; null = default Kitsune look. */
    val appliedThemeId: String? = secureStorage.getString(SecureStorage.KEY_APPLIED_TIMELINE_THEME)

    private val _suggestions = MutableStateFlow<List<String>?>(null)
    val suggestions: StateFlow<List<String>?> = _suggestions.asStateFlow()

    private val _isLoadingSuggestions = MutableStateFlow(false)
    val isLoadingSuggestions: StateFlow<Boolean> = _isLoadingSuggestions.asStateFlow()

    fun deleteMoment(moment: KeyMomentEntity) {
        viewModelScope.launch { keyMomentRepository.delete(moment) }
    }

    fun generateDirections() {
        viewModelScope.launch {
            _isLoadingSuggestions.value = true
            _suggestions.value = scriptDoctorUseCase.suggestDirections(chatId)
            _isLoadingSuggestions.value = false
        }
    }

    fun dismissSuggestions() {
        _suggestions.value = null
    }
}