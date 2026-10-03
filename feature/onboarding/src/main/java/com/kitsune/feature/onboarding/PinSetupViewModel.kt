package com.kitsune.feature.onboarding

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.security.vault.VaultKeyProvider
import com.kitsune.core.security.vault.VaultUnlockResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_PIN_LENGTH = 4

sealed interface PinSetupUiState {
    data object Idle : PinSetupUiState
    data object Loading : PinSetupUiState
    data object Success : PinSetupUiState
    data class Error(val message: String) : PinSetupUiState
}

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val vaultKeyProvider: VaultKeyProvider,
    private val databaseProvider: KitsuneDatabaseProvider
) : ViewModel() {

    private val _state = MutableStateFlow<PinSetupUiState>(PinSetupUiState.Idle)
    val state: StateFlow<PinSetupUiState> = _state.asStateFlow()

    fun submit(activity: FragmentActivity, pin: CharArray, confirmPin: CharArray) {
        if (pin.size < MIN_PIN_LENGTH) {
            _state.value = PinSetupUiState.Error("Le code doit contenir au moins $MIN_PIN_LENGTH chiffres.")
            return
        }
        if (!pin.contentEquals(confirmPin)) {
            _state.value = PinSetupUiState.Error("Les deux codes ne correspondent pas.")
            return
        }

        viewModelScope.launch {
            _state.value = PinSetupUiState.Loading
            when (val result = vaultKeyProvider.initialize(activity, pin)) {
                is VaultUnlockResult.Unlocked -> {
                    databaseProvider.open(result.passphrase)
                    _state.value = PinSetupUiState.Success
                }
                is VaultUnlockResult.BiometricFailed -> _state.value = PinSetupUiState.Error(result.message)
                VaultUnlockResult.BiometricCancelled -> _state.value = PinSetupUiState.Idle
                VaultUnlockResult.WrongPin, VaultUnlockResult.NotInitialized ->
                    _state.value = PinSetupUiState.Error("Erreur inattendue lors de la configuration.")
            }
        }
    }
}
