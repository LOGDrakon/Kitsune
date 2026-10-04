package com.kitsune.feature.settings.generation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.network.catalog.ModelCatalogRepository
import com.kitsune.core.network.preferences.GenerationPreferences
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.preferences.MemoryDepth
import com.kitsune.core.network.preferences.NetworkPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenerationUiState(
    val depth: MemoryDepth,
    val rawWindow: Int,
    val loreEntries: Int,
    val maxReplyTokens: Int,
    val enhancedCraft: Boolean,
    /** The story model's context window in tokens, when its provider publishes it. */
    val chatModelContext: Int? = null
)

@HiltViewModel
class GenerationViewModel @Inject constructor(
    private val preferences: GenerationPreferences,
    private val networkPreferences: NetworkPreferences,
    private val modelCatalogRepository: ModelCatalogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<GenerationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ref = networkPreferences.getModelForOperation(LlmOperation.CHAT)
            val context = modelCatalogRepository.getModels(forceRefresh = false).getOrNull()
                ?.firstOrNull { it.id == ref }?.maxInputTokens
            _state.value = _state.value.copy(chatModelContext = context)
        }
    }

    fun setDepth(depth: MemoryDepth) {
        preferences.setMemoryDepth(depth)
        refresh()
    }

    fun setRawWindow(value: Int) {
        preferences.setRawWindow(value)
        refresh()
    }

    fun setLoreEntries(value: Int) {
        preferences.setLoreEntries(value)
        refresh()
    }

    fun setMaxReplyTokens(value: Int) {
        preferences.setMaxReplyTokens(value)
        refresh()
    }

    fun setEnhancedCraft(enabled: Boolean) {
        preferences.setEnhancedCraftEnabled(enabled)
        refresh()
    }

    private fun refresh() {
        _state.value = load().copy(chatModelContext = _state.value.chatModelContext)
    }

    private fun load() = GenerationUiState(
        depth = preferences.memoryDepth(),
        rawWindow = preferences.rawWindow(),
        loreEntries = preferences.loreEntries(),
        maxReplyTokens = preferences.maxReplyTokens(),
        enhancedCraft = preferences.isEnhancedCraftEnabled()
    )
}
