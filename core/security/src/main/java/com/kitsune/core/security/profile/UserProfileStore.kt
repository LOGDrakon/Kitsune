package com.kitsune.core.security.profile

import com.kitsune.core.security.storage.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileStore @Inject constructor(private val secureStorage: SecureStorage) {

    fun get(): UserProfile = UserProfile(
        firstName = secureStorage.getString(SecureStorage.KEY_USER_FIRST_NAME).orEmpty(),
        lastName = secureStorage.getString(SecureStorage.KEY_USER_LAST_NAME).orEmpty(),
        pronoun = secureStorage.getString(SecureStorage.KEY_USER_PRONOUN).orEmpty(),
        age = secureStorage.getString(SecureStorage.KEY_USER_AGE).orEmpty(),
        physicalDescription = secureStorage.getString(SecureStorage.KEY_USER_PHYSICAL_DESCRIPTION).orEmpty(),
        sexualOrientation = secureStorage.getString(SecureStorage.KEY_USER_SEXUAL_ORIENTATION).orEmpty()
    )

    fun save(profile: UserProfile) {
        secureStorage.putString(SecureStorage.KEY_USER_FIRST_NAME, profile.firstName)
        secureStorage.putString(SecureStorage.KEY_USER_LAST_NAME, profile.lastName)
        secureStorage.putString(SecureStorage.KEY_USER_PRONOUN, profile.pronoun)
        secureStorage.putString(SecureStorage.KEY_USER_AGE, profile.age)
        secureStorage.putString(SecureStorage.KEY_USER_PHYSICAL_DESCRIPTION, profile.physicalDescription)
        secureStorage.putString(SecureStorage.KEY_USER_SEXUAL_ORIENTATION, profile.sexualOrientation)
    }
}
