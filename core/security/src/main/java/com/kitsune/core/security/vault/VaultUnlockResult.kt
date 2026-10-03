package com.kitsune.core.security.vault

import com.kitsune.core.security.model.PinSlot

sealed interface VaultUnlockResult {
    class Unlocked(val passphrase: ByteArray, val slot: PinSlot) : VaultUnlockResult
    data object WrongPin : VaultUnlockResult
    data object NotInitialized : VaultUnlockResult
    class BiometricFailed(val errorCode: Int, val message: String) : VaultUnlockResult
    data object BiometricCancelled : VaultUnlockResult
}
