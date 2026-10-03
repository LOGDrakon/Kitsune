package com.kitsune.feature.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.CreatorFollowsManager
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.model.FollowedCreator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyFollowsViewModel @Inject constructor(
    private val backendClient: KitsuneBackendClient,
    private val creatorFollowsManager: CreatorFollowsManager
) : ViewModel() {

    private val _creators = MutableStateFlow<List<FollowedCreator>>(emptyList())
    val creators: StateFlow<List<FollowedCreator>> = _creators.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        load()
        // Opening this screen IS "seeing" what's new — clears the badge the same way opening "Mes
        // messages" implicitly marks messages seen, rather than requiring a separate action.
        viewModelScope.launch { creatorFollowsManager.markSeen() }
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            backendClient.getFollowedCreators()
                .onSuccess { response -> _creators.value = response.creators }
            _isLoading.value = false
        }
    }

    fun unfollow(creatorId: String) {
        viewModelScope.launch {
            backendClient.unfollowCreator(creatorId).onSuccess {
                _creators.value = _creators.value.filterNot { it.creatorId == creatorId }
            }
        }
    }
}
