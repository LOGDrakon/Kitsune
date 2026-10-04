package com.kitsune.core.security.wellbeing

import com.kitsune.core.security.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An optional, gentle reminder to take a break (PRINCIPLES.md §3), **off by default**.
 *
 * A session is time spent with the app in the foreground; leaving it for [SESSION_GAP_MS] or more
 * starts a new one. When a session reaches the interval the user chose, [due] turns true once. The
 * reminder is a question, never a lock: "continue" simply starts counting again, and nothing is ever
 * reported or remembered beyond the current session.
 */
@Singleton
class BreakReminder @Inject constructor(private val secureStorage: SecureStorage) {

    private val _due = MutableStateFlow(false)
    val due: StateFlow<Boolean> = _due.asStateFlow()

    private var countingFrom = 0L
    private var backgroundedAt = 0L

    /** Minutes of continuous use before the reminder, or 0 when it is off. */
    fun intervalMinutes(): Int = secureStorage.getInt(SecureStorage.KEY_BREAK_REMINDER_MINUTES, 0)

    fun setIntervalMinutes(minutes: Int) {
        secureStorage.putInt(SecureStorage.KEY_BREAK_REMINDER_MINUTES, minutes.coerceAtLeast(0))
        countingFrom = System.currentTimeMillis()
        _due.value = false
    }

    fun onAppForegrounded(now: Long = System.currentTimeMillis()) {
        if (countingFrom == 0L || backgroundedAt == 0L || now - backgroundedAt >= SESSION_GAP_MS) {
            countingFrom = now
            _due.value = false
        }
    }

    fun onAppBackgrounded(now: Long = System.currentTimeMillis()) {
        backgroundedAt = now
    }

    /** Called periodically while the app is in the foreground. */
    fun tick(now: Long = System.currentTimeMillis()) {
        val minutes = intervalMinutes()
        if (minutes <= 0 || countingFrom == 0L || _due.value) return
        if (now - countingFrom >= minutes * 60_000L) _due.value = true
    }

    /** "Continue": dismiss, and remind again after another full interval. */
    fun snooze(now: Long = System.currentTimeMillis()) {
        countingFrom = now
        _due.value = false
    }

    companion object {
        const val SESSION_GAP_MS = 10 * 60_000L
        /** The intervals offered in Settings; 0 = off. */
        val CHOICES = listOf(0, 60, 120, 180)
    }
}
