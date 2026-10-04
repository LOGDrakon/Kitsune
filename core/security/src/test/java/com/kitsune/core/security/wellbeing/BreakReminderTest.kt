package com.kitsune.core.security.wellbeing

import com.kitsune.core.security.storage.SecureStorage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fakeSecureStorage(): SecureStorage {
    val ints = mutableMapOf<String, Int>()
    return mockk {
        every { putInt(any(), any()) } answers { ints[firstArg()] = secondArg() }
        every { getInt(any(), any()) } answers { ints[firstArg()] ?: secondArg() }
    }
}

class BreakReminderTest {

    private val minute = 60_000L

    @Test
    fun `off by default, never due`() {
        val reminder = BreakReminder(fakeSecureStorage())
        reminder.onAppForegrounded(now = 0L + 1)
        reminder.tick(now = 10 * 60 * minute)
        assertFalse(reminder.due.value)
    }

    @Test
    fun `due after the chosen interval of continuous use, and snooze restarts the count`() {
        val reminder = BreakReminder(fakeSecureStorage())
        reminder.setIntervalMinutes(60)
        reminder.onAppForegrounded(now = 1L)
        reminder.tick(now = 59 * minute)
        assertFalse(reminder.due.value)
        reminder.tick(now = 61 * minute)
        assertTrue(reminder.due.value)

        reminder.snooze(now = 61 * minute)
        assertFalse(reminder.due.value)
        reminder.tick(now = 100 * minute)
        assertFalse(reminder.due.value)
        reminder.tick(now = 122 * minute)
        assertTrue(reminder.due.value)
    }

    @Test
    fun `a short trip to the background keeps the session, a long one starts a new session`() {
        val reminder = BreakReminder(fakeSecureStorage())
        reminder.setIntervalMinutes(60)
        reminder.onAppForegrounded(now = 1L)
        reminder.onAppBackgrounded(now = 30 * minute)
        reminder.onAppForegrounded(now = 35 * minute)
        reminder.tick(now = 61 * minute)
        assertTrue(reminder.due.value)

        reminder.onAppBackgrounded(now = 62 * minute)
        reminder.onAppForegrounded(now = 80 * minute)
        assertFalse(reminder.due.value)
        reminder.tick(now = 130 * minute)
        assertFalse(reminder.due.value)
    }
}
