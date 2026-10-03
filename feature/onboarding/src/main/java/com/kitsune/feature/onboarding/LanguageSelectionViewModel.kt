package com.kitsune.feature.onboarding

import androidx.lifecycle.ViewModel
import com.kitsune.core.security.locale.AppLanguage
import com.kitsune.core.security.locale.AppLanguageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LanguageSelectionViewModel @Inject constructor(
    private val appLanguageManager: AppLanguageManager
) : ViewModel() {

    val languages: List<AppLanguage> = AppLanguage.entries

    fun selectLanguage(language: AppLanguage) {
        appLanguageManager.setSelectedLanguage(language)
    }
}
