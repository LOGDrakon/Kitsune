package com.kitsune.core.moderation.safeword

import com.kitsune.core.security.storage.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/** Configurable safe word (FEATURES.md section 3) that immediately interrupts a scene when typed in chat. */
@Singleton
class SafeWordManager @Inject constructor(private val secureStorage: SecureStorage) {

    fun getSafeWord(): String? = secureStorage.getString(SecureStorage.KEY_SAFE_WORD)?.takeIf { it.isNotBlank() }

    fun setSafeWord(word: String) {
        secureStorage.putString(SecureStorage.KEY_SAFE_WORD, word.trim())
    }

    fun clearSafeWord() {
        secureStorage.remove(SecureStorage.KEY_SAFE_WORD)
    }

    fun matches(text: String): Boolean {
        val safeWord = getSafeWord() ?: return false
        return text.trim().equals(safeWord, ignoreCase = true)
    }
}
