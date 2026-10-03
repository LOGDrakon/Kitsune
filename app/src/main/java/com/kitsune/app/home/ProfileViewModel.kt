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
 * v1 had no profile: the user's identity (username), their creator standing (badges, listings,
 * followers), their subscription state and their credit balance were spread across Settings, a
 * "Mes badges" screen, a "Mes abonnements" screen and a shopping-cart icon. There was nowhere to
 * answer "who am I in this app and what do I have".
 *
 * Every field here is loaded defensively: this tab must render for a brand-new anonymous account
 * with no username, no listings and no network, so a failed call leaves a null and the screen simply
 * omits that block rather than showing an error.
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
        /** Awarded badges only — progress towards unearned ones belongs on the badges screen. */
        val badgeCount: Int get() = creatorProfile?.badges?.count { it.awarded } ?: 0
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val creditBalance: StateFlow<Int> = backendClient.creditBalance
    val dailyFreeRemaining: StateFlow<Int> = backendClient.dailyFreeCreditsRemaining

    /** The user's own creator id, needed to open their public listings page. Null offline. */
    val userId: String? get() = backendClient.getUserId()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            backendClient.refreshBalance()

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
