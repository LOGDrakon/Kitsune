package com.kitsune.feature.auth

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.database.DecoyNotesDatabaseProvider
import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.security.lock.AutoLockManager
import com.kitsune.core.security.model.PinSlot
import com.kitsune.core.security.model.VaultSecurityMode
import com.kitsune.core.security.vault.VaultKeyProvider
import com.kitsune.core.security.vault.VaultUnlockResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LockUiState {
    data object Idle : LockUiState
    data object Loading : LockUiState
    data object UnlockedReal : LockUiState
    data object UnlockedPanic : LockUiState
    data class Error(val message: String) : LockUiState
}

@HiltViewModel
class LockScreenViewModel @Inject constructor(
    private val vaultKeyProvider: VaultKeyProvider,
    private val databaseProvider: KitsuneDatabaseProvider,
    private val decoyNotesDatabaseProvider: DecoyNotesDatabaseProvider,
    private val autoLockManager: AutoLockManager
) : ViewModel() {

    private val _state = MutableStateFlow<LockUiState>(LockUiState.Idle)
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private val _securityMode = MutableStateFlow<VaultSecurityMode?>(null)
    val securityMode: StateFlow<VaultSecurityMode?> = _securityMode.asStateFlow()

    init {
        viewModelScope.launch { _securityMode.value = vaultKeyProvider.getSecurityMode() }
    }

    fun unlock(activity: FragmentActivity, pin: CharArray) {
        viewModelScope.launch {
            _state.value = LockUiState.Loading
            when (val result = vaultKeyProvider.unlock(activity, pin)) {
                is VaultUnlockResult.Unlocked -> {
                    if (result.slot == PinSlot.PANIC) {
                        decoyNotesDatabaseProvider.open(result.passphrase)
                        _state.value = LockUiState.UnlockedPanic
                    } else {
                        databaseProvider.open(result.passphrase)
                        autoLockManager.markUnlocked()
                        _state.value = LockUiState.UnlockedReal
                    }
                }
                VaultUnlockResult.WrongPin -> _state.value = LockUiState.Error("Code incorrect.")
                VaultUnlockResult.NotInitialized -> _state.value = LockUiState.Error("Coffre non initialisé.")
                is VaultUnlockResult.BiometricFailed -> _state.value = LockUiState.Error(result.message)
                VaultUnlockResult.BiometricCancelled -> _state.value = LockUiState.Idle
            }
        }
    }
}
