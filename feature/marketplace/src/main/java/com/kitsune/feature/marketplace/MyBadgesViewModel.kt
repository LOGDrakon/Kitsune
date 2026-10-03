package com.kitsune.feature.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.model.CreatorProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs "Mes badges" (Settings → Compte) — the caller's own badges/progression, fetched via the
 * same `GET /marketplace/creators/{userId}/profile` aggregate endpoint the public creator profile
 * header uses (`MarketplaceViewModel.loadCreatorProfile`), just called with one's own id. */
@HiltViewModel
class MyBadgesViewModel @Inject constructor(
    private val backendClient: KitsuneBackendClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<MyBadgesUiState>(MyBadgesUiState.Loading)
    val uiState: StateFlow<MyBadgesUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = MyBadgesUiState.Loading
            val userId = backendClient.getUserId()
            if (userId == null) {
                _uiState.value = MyBadgesUiState.Error
                return@launch
            }
            backendClient.getCreatorProfile(userId).fold(
                onSuccess = { profile -> _uiState.value = MyBadgesUiState.Ready(profile) },
                onFailure = { _uiState.value = MyBadgesUiState.Error }
            )
        }
    }
}

sealed interface MyBadgesUiState {
    data object Loading : MyBadgesUiState
    data class Ready(val profile: CreatorProfile) : MyBadgesUiState
    data object Error : MyBadgesUiState
}
