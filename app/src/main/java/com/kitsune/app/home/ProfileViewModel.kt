package com.kitsune.app.home

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

/**
 * Everything the Profile tab shows, in one place.
 *
 * The profile is the user's marketplace identity: username, creator standing (listings,
 * followers), follows, moderation messages and idea proposals. Everything else about them lives on
 * this phone and needs no profile.
 *
 * Every field is loaded defensively: this tab must render with the marketplace switched off, with no
 * account yet, and with no network — a failed call leaves a null and the screen omits that block.
 * Opening it never registers an account: that happens on first real marketplace use.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val backendClient: KitsuneBackendClient
) : ViewModel() {

    data class State(
        val username: String? = null,
        val creatorProfile: CreatorProfile? = null,
        val followedCount: Int = 0,
        val newFollowedListings: Int = 0,
        val unreadMessages: Int = 0,
        val loading: Boolean = true
    ) {
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val marketplaceEnabled: StateFlow<Boolean> = backendClient.enabled
    val authState = backendClient.authState

    /** The user's own creator id, needed to open their public listings page. Null offline. */
    val userId: String? get() = backendClient.getUserId()

    init {
        refresh()
    }

    fun refresh() {
        if (!backendClient.isEnabled() || !backendClient.isAuthenticated()) {
            _state.value = State(loading = false)
            return
        }
        viewModelScope.launch {
            val username = backendClient.getUserProfile().getOrNull()?.username
            val ownId = backendClient.getUserId()
            val creator = ownId?.let { backendClient.getCreatorProfile(it).getOrNull() }
            val follows = backendClient.getFollowedCreators().getOrNull()?.creators?.size ?: 0
            val newListings = backendClient.getNewFollowedListingsCount().getOrNull() ?: 0
            val unread = backendClient.getUserMessages().getOrNull()?.size ?: 0

            _state.value = State(
                username = username,
                creatorProfile = creator,
                followedCount = follows,
                newFollowedListings = newListings,
                unreadMessages = unread,
                loading = false
            )
        }
    }
}
