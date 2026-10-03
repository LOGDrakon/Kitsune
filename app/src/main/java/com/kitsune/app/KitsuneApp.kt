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
import com.kitsune.core.common.memory.MemorySettingsHolder
import com.kitsune.core.network.preferences.NetworkPreferences
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.lock.AutoLockManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How often the app pings the backend while foregrounded, to derive session/engagement
 * telemetry (must stay well under SessionTrackingService's server-side session gap). */
private const val SESSION_HEARTBEAT_INTERVAL_MS = 4 * 60 * 1000L

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
    lateinit var networkPreferences: NetworkPreferences

    @Inject
    lateinit var appLanguageManager: AppLanguageManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        appLanguageManager.applyCurrentLanguage()
        WorkManager.initialize(this, workManagerConfiguration)

        appScope.launch { runCatching { generationJobRepository.failStalePendingJobs() } }

        // Auto-register anonymous account if not yet authenticated
        appScope.launch {
            if (!backendClient.isAuthenticated()) {
                runCatching { backendClient.registerAnonymous() }
            }
            // Populates backendClient.accountLockReason so a banned/frozen account gets locked
            // out from app start, not just whenever the user happens to open Settings.
            runCatching { backendClient.getUserProfile() }
            // Fetch model config from backend and apply to NetworkPreferences
            runCatching {
                backendClient.fetchModelConfig().onSuccess { config ->
                    networkPreferences.applyBackendModelConfig(config)
                    MemorySettingsHolder.apply(
                        maxContextTokens = config.maxContextTokens,
                        rawWindowSize = config.rawWindowSize,
                        rawWindowSizePro = config.rawWindowSizePro,
                        loreEntries = config.loreEntries,
                        loreEntriesPro = config.loreEntriesPro,
                        samplingEnabled = config.samplingEnabled
                    )
                }
            }
            runCatching { announcementManager.fetchActiveAnnouncements() }
            runCatching { userMessageManager.refresh() }
            runCatching { creatorFollowsManager.refresh() }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                autoLockManager.onAppBackgrounded()
                heartbeatJob?.cancel()
                heartbeatJob = null
            }

            override fun onStart(owner: LifecycleOwner) {
                autoLockManager.onAppForegrounded()
                heartbeatJob = appScope.launch {
                    while (true) {
                        runCatching { backendClient.sendSessionHeartbeat() }
                        delay(SESSION_HEARTBEAT_INTERVAL_MS)
                    }
                }
            }
        })
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
