package com.kitsune.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import com.kitsune.core.backend.AnnouncementManager
import com.kitsune.core.backend.CreatorFollowsManager
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.UserMessageManager
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.lock.AutoLockManager
import com.kitsune.core.security.wellbeing.BreakReminder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Startup makes no network request on its own account: there is no telemetry, no remote
 * configuration and no automatic registration. The only calls here refresh marketplace inboxes, and
 * only for someone who already has a marketplace session on a marketplace they left switched on.
 */
@HiltAndroidApp
class KitsuneApp : Application(), Configuration.Provider {

    @Inject
    lateinit var autoLockManager: AutoLockManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var generationJobRepository: GenerationJobRepository

    @Inject
    lateinit var backendClient: KitsuneBackendClient

    @Inject
    lateinit var announcementManager: AnnouncementManager

    @Inject
    lateinit var userMessageManager: UserMessageManager

    @Inject
    lateinit var creatorFollowsManager: CreatorFollowsManager

    @Inject
    lateinit var appLanguageManager: AppLanguageManager

    @Inject
    lateinit var breakReminder: BreakReminder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appLanguageManager.applyCurrentLanguage()
        WorkManager.initialize(this, workManagerConfiguration)

        appScope.launch { runCatching { generationJobRepository.failStalePendingJobs() } }

        appScope.launch {
            if (backendClient.isEnabled() && backendClient.isAuthenticated()) {
                // Populates accountLockReason so a banned account sees why publishing fails.
                runCatching { backendClient.getUserProfile() }
                runCatching { announcementManager.fetchActiveAnnouncements() }
                runCatching { userMessageManager.refresh() }
                runCatching { creatorFollowsManager.refresh() }
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                autoLockManager.onAppBackgrounded()
                breakReminder.onAppBackgrounded()
            }

            override fun onStart(owner: LifecycleOwner) {
                autoLockManager.onAppForegrounded()
                breakReminder.onAppForegrounded()
            }
        })
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
