package com.kitsune.core.security.model

import androidx.biometric.BiometricPrompt

sealed interface BiometricAuthResult {
    data class Success(val cryptoObject: BiometricPrompt.CryptoObject?) : BiometricAuthResult
    data class Failed(val errorCode: Int, val message: String) : BiometricAuthResult
    data object Cancelled : BiometricAuthResult
}
