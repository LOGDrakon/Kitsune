package com.kitsune.app

import androidx.fragment.app.FragmentActivity

/**
 * THROWAWAY — biometric-prompt host for app/src/androidTest/.../DatabaseRescueTest.kt only.
 * Deliberately NOT MainActivity, so the rescue test doesn't also trigger the app's own real
 * (crashing) startup unlock path. Delete alongside that test once the database is recovered.
 */
class RescueHostActivity : FragmentActivity()
