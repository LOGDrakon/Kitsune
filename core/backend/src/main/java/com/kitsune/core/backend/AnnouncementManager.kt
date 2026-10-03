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
        if (!backendClient.isEnabled() || !backendClient.isAuthenticated()) return@withContext
        backendClient.getActiveAnnouncements()
            .onSuccess { _pendingAnnouncements.value = it }
            .onFailure { }
    }

    suspend fun dismiss(announcementId: String) = withContext(dispatcherProvider.io) {
        backendClient.dismissAnnouncement(announcementId)
        _pendingAnnouncements.value = _pendingAnnouncements.value.filterNot { it.id == announcementId }
    }
}
