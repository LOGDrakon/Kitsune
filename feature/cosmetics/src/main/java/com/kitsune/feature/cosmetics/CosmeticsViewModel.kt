package com.kitsune.feature.cosmetics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.model.*
import com.kitsune.core.security.storage.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CosmeticsViewModel @Inject constructor(
    private val backendClient: KitsuneBackendClient,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow<CosmeticsUiState>(CosmeticsUiState.Loading)
    val uiState: StateFlow<CosmeticsUiState> = _uiState.asStateFlow()

    private val _ownedCosmetics = MutableStateFlow<Set<String>>(emptySet())
    val ownedCosmetics: StateFlow<Set<String>> = _ownedCosmetics.asStateFlow()

    private val _appliedCosmetics = MutableStateFlow<Set<String>>(emptySet())
    val appliedCosmetics: StateFlow<Set<String>> = _appliedCosmetics.asStateFlow()

    init {
        val applied = mutableSetOf<String>()
        secureStorage.getString(SecureStorage.KEY_APPLIED_STYLE_PACK)?.let { applied += it }
        secureStorage.getString(SecureStorage.KEY_APPLIED_TIMELINE_THEME)?.let { applied += it }
        _appliedCosmetics.value = applied
        loadCosmetics()
    }

    fun refresh() = loadCosmetics()

    private fun loadCosmetics() {
        viewModelScope.launch {
            try {
                val catalogResult = backendClient.getCosmeticCatalog()
                val ownedResult = backendClient.getOwnedCosmetics()

                catalogResult.fold(
                    onSuccess = { catalog ->
                        val owned = ownedResult.getOrNull()?.cosmeticIds?.toSet() ?: emptySet()
                        _ownedCosmetics.value = owned
                        _uiState.value = CosmeticsUiState.Ready(catalog.items)
                    },
                    onFailure = { e ->
                        _uiState.value = CosmeticsUiState.Error(e.message ?: "Failed to load cosmetics")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = CosmeticsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun applyCosmetic(cosmeticId: String) {
        viewModelScope.launch {
            val item = (_uiState.value as? CosmeticsUiState.Ready)?.items?.find { it.id == cosmeticId } ?: return@launch
            when (item.category) {
                "STYLE_PACK" -> {
                    secureStorage.putString(SecureStorage.KEY_APPLIED_STYLE_PACK, cosmeticId)
                    replaceAppliedWithinCategory("STYLE_PACK", cosmeticId)
                }
                "TIMELINE_THEME" -> {
                    secureStorage.putString(SecureStorage.KEY_APPLIED_TIMELINE_THEME, cosmeticId)
                    replaceAppliedWithinCategory("TIMELINE_THEME", cosmeticId)
                }
                else -> _appliedCosmetics.value = _appliedCosmetics.value + cosmeticId
            }
        }
    }

    /** STYLE_PACK and TIMELINE_THEME are single-selection: applying a new one supersedes any
     * previously applied item of the same category, both in [secureStorage] (single key each,
     * handled by the caller) and in the in-memory [_appliedCosmetics] set. */
    private fun replaceAppliedWithinCategory(category: String, cosmeticId: String) {
        val itemsById = (_uiState.value as? CosmeticsUiState.Ready)?.items?.associateBy { it.id } ?: emptyMap()
        val withoutCategory = _appliedCosmetics.value.filter { itemsById[it]?.category != category }.toSet()
        _appliedCosmetics.value = withoutCategory + cosmeticId
    }

    fun removeCosmetic(cosmeticId: String) {
        viewModelScope.launch {
            val item = (_uiState.value as? CosmeticsUiState.Ready)?.items?.find { it.id == cosmeticId }
            when (item?.category) {
                "STYLE_PACK" -> secureStorage.remove(SecureStorage.KEY_APPLIED_STYLE_PACK)
                "TIMELINE_THEME" -> secureStorage.remove(SecureStorage.KEY_APPLIED_TIMELINE_THEME)
                else -> {}
            }
            _appliedCosmetics.value = _appliedCosmetics.value - cosmeticId
        }
    }
}

sealed class CosmeticsUiState {
    data object Loading : CosmeticsUiState()
    data class Ready(val items: List<CosmeticItem>) : CosmeticsUiState()
    data class Error(val message: String) : CosmeticsUiState()
}
