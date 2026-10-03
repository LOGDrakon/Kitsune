package com.kitsune.core.backend

import com.kitsune.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors [UserMessageManager]'s pattern for "Mes abonnements" (following marketplace creators) —
 * there is no push-notification infrastructure anywhere in this backend, so "you have new listings
 * from people you follow" is a badge count fetched once at app launch, same as the moderation-
 * message unread count, rather than a real-time notification. [refresh] only ever reads the count
 * (never marks it seen) — that only happens when the user actually opens "Mes abonnements"
 * ([markSeen]), exactly like [UserMessageManager.markRead] only fires from within the inbox screen. */
@Singleton
class CreatorFollowsManager @Inject constructor(
    private val backendClient: KitsuneBackendClient,
    private val dispatcherProvider: DispatcherProvider
) {
    private val _newListingsCount = MutableStateFlow(0)
    val newListingsCount: StateFlow<Int> = _newListingsCount.asStateFlow()

    suspend fun refresh() = withContext(dispatcherProvider.io) {
        if (!backendClient.isEnabled() || !backendClient.isAuthenticated()) return@withContext
        backendClient.getNewFollowedListingsCount()
            .onSuccess { count -> _newListingsCount.value = count }
            .onFailure { }
    }

    suspend fun markSeen() = withContext(dispatcherProvider.io) {
        backendClient.markFollowsSeen()
        _newListingsCount.value = 0
    }
}
