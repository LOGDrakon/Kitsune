package com.kitsune.core.security.onboarding

import com.kitsune.core.security.storage.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the outcome of the onboarding age check (see FEATURES.md section 3). This only
 * records that an adult date of birth was entered once — it is not tied to any persona's
 * (separately locked) age field.
 */
@Singleton
class AgeVerificationStore @Inject constructor(
    private val secureStorage: SecureStorage
) {

    fun isVerifiedAdult(): Boolean = secureStorage.getInt(SecureStorage.KEY_AGE_VERIFIED_ADULT, 0) == 1

    fun markVerifiedAdult() {
        secureStorage.putInt(SecureStorage.KEY_AGE_VERIFIED_ADULT, 1)
    }
}
