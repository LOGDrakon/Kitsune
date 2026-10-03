package com.kitsune.core.backend

import com.kitsune.core.backend.model.AnnouncementResponse
import com.kitsune.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnouncementManager @Inject constructor(
    private val backendClient: KitsuneBackendClient,
    private val dispatcherProvider: DispatcherProvider
) {
    private val _pendingAnnouncements = MutableStateFlow<List<AnnouncementResponse>>(emptyList())
    val pendingAnnouncements: StateFlow<List<AnnouncementResponse>> = _pendingAnnouncements.asStateFlow()

    suspend fun fetchActiveAnnouncements() = withContext(dispatcherProvider.io) {
        if (!backendClient.isAuthenticated()) return@withContext
        backendClient.getActiveAnnouncements()
            .onSuccess { _pendingAnnouncements.value = it }
            .onFailure { }
    }

    /** Returns the number of credits granted by this dismissal, or null if the announcement carried
     * no reward (the common case) — lets the caller show a confirmation without a second network
     * round trip; the balance itself is already synced by [KitsuneBackendClient.dismissAnnouncement]. */
    suspend fun dismiss(announcementId: String): Int? = withContext(dispatcherProvider.io) {
        val rewardCredits = backendClient.dismissAnnouncement(announcementId).getOrNull()?.rewardCredits
        _pendingAnnouncements.value = _pendingAnnouncements.value.filterNot { it.id == announcementId }
        rewardCredits
    }
}
