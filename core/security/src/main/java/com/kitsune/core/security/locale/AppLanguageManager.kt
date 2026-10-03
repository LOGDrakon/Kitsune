package com.kitsune.core.security.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.kitsune.core.security.storage.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Explicit in-app language override, independent of the device's system locale — chosen once on
 * first launch (see `feature:onboarding`'s language selection step) and changeable any time after
 * from Settings. Persisted separately from [AppCompatDelegate]'s own per-app language storage so
 * we can tell "never chosen yet" (first launch) apart from "explicitly set to English".
 */
@Singleton
class AppLanguageManager @Inject constructor(
    private val secureStorage: SecureStorage
) {

    /** False only before the user has ever picked a language (drives the onboarding gate). */
    fun hasSelectedLanguage(): Boolean = secureStorage.contains(SecureStorage.KEY_APP_LANGUAGE_SELECTED)

    fun getSelectedLanguage(): AppLanguage =
        AppLanguage.fromTag(secureStorage.getString(SecureStorage.KEY_APP_LANGUAGE_SELECTED)) ?: AppLanguage.ENGLISH

    /** Persists [language] and applies it immediately app-wide via the AndroidX per-app language API. */
    fun setSelectedLanguage(language: AppLanguage) {
        secureStorage.putString(SecureStorage.KEY_APP_LANGUAGE_SELECTED, language.languageTag)
        applyCurrentLanguage()
    }

    /** Re-applies the persisted choice — call once at process start, before any UI is shown. */
    fun applyCurrentLanguage() {
        val tag = if (hasSelectedLanguage()) getSelectedLanguage().languageTag else null
        AppCompatDelegate.setApplicationLocales(
            if (tag != null) LocaleListCompat.forLanguageTags(tag) else LocaleListCompat.getEmptyLocaleList()
        )
    }
}
