package com.kitsune.core.backend

import com.kitsune.core.backend.model.UserMessageResponse
import com.kitsune.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors [AnnouncementManager]'s pattern for the user's one-way moderation-message inbox — see
 * `UserMessagingService` (KitsuneBackend). Unlike announcements (broadcast, dismiss-only), messages
 * are targeted at this one user and explicitly marked read rather than dismissed/discarded. */
@Singleton
class UserMessageManager @Inject constructor(
    private val backendClient: KitsuneBackendClient,
    private val dispatcherProvider: DispatcherProvider
) {
    private val _messages = MutableStateFlow<List<UserMessageResponse>>(emptyList())
    val messages: StateFlow<List<UserMessageResponse>> = _messages.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    suspend fun refresh() = withContext(dispatcherProvider.io) {
        if (!backendClient.isEnabled() || !backendClient.isAuthenticated()) return@withContext
        backendClient.getUserMessages()
            .onSuccess { list ->
                _messages.value = list
                _unreadCount.value = list.count { it.readAt == null }
            }
            .onFailure { }
    }

    suspend fun markRead(messageId: String) = withContext(dispatcherProvider.io) {
        backendClient.markUserMessageRead(messageId)
        refresh()
    }
}
