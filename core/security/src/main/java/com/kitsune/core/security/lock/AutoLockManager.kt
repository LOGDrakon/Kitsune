package com.kitsune.core.security.lock

import com.kitsune.core.security.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the vault should be considered locked. Callers wire [onAppBackgrounded] and
 * [onAppForegrounded] to process-lifecycle events; the actual timeout check only happens on
 * the way back to the foreground, so the app never needs a running background timer.
 */
@Singleton
class AutoLockManager @Inject constructor(private val secureStorage: SecureStorage) {

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundedAtMillis: Long? = null

    var timeoutSeconds: Int
        get() = secureStorage.getInt(SecureStorage.KEY_AUTO_LOCK_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
        set(value) = secureStorage.putInt(SecureStorage.KEY_AUTO_LOCK_TIMEOUT_SECONDS, value)

    fun markUnlocked() {
        _isLocked.value = false
        backgroundedAtMillis = null
    }

    fun lockNow() {
        _isLocked.value = true
        backgroundedAtMillis = null
    }

    fun onAppBackgrounded() {
        backgroundedAtMillis = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        val backgroundedAt = backgroundedAtMillis ?: return
        val elapsedSeconds = (System.currentTimeMillis() - backgroundedAt) / 1000
        if (elapsedSeconds >= timeoutSeconds) {
            _isLocked.value = true
        }
        backgroundedAtMillis = null
    }

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 60
    }
}
