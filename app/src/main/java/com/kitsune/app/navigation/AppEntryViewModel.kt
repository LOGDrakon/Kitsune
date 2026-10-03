package com.kitsune.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.onboarding.AgeVerificationStore
import com.kitsune.core.security.vault.VaultKeyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppEntryViewModel @Inject constructor(
    private val appLanguageManager: AppLanguageManager,
    private val ageVerificationStore: AgeVerificationStore,
    private val vaultKeyProvider: VaultKeyProvider
) : ViewModel() {

    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            // Order matters: this only runs once per cold start, so every branch below other
            // than the first one is necessarily resuming after the process was killed mid-setup.
            // `isInitialized()`/`isVerifiedAdult()` checked first (most-advanced state wins) so a
            // user who's already past account choice — genuinely mid "create new account", past
            // age verification, just not through PIN setup yet — isn't bounced backwards. Anyone
            // NOT that far along (including the entire "recover account" branch, which never
            // touches age verification at all) falls through to ACCOUNT_CHOICE: that's the real
            // next screen after language selection in the current nav graph, not AGE_VERIFICATION
            // — this `when` simply hadn't been updated when AccountChoiceScreen/account recovery
            // were added, so a restart mid-choice or mid-recovery used to skip straight past it.
            _startRoute.value = when {
                !appLanguageManager.hasSelectedLanguage() -> Routes.LANGUAGE_SELECTION
                vaultKeyProvider.isInitialized() -> Routes.LOCK
                ageVerificationStore.isVerifiedAdult() -> Routes.PIN_SETUP
                else -> Routes.ACCOUNT_CHOICE
            }
        }
    }
}
