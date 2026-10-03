package com.kitsune.core.security.pin

import com.kitsune.core.security.model.PinSlot
import com.kitsune.core.security.model.PinVerificationResult
import com.kitsune.core.security.storage.SecureStorage
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies the app-level PIN (Argon2id-derived, separate from the system biometric/PIN lock —
 * see FEATURES.md section 2). The same Argon2id output doubles as key material fed into
 * [com.kitsune.core.security.vault.VaultKeyProvider], so a correct PIN is required to derive
 * the database passphrase, not just to pass a UI gate.
 */
@Singleton
class PinCredentialManager @Inject constructor(
    private val secureStorage: SecureStorage,
    private val hasher: Argon2Hasher
) {

    fun isConfigured(slot: PinSlot): Boolean =
        secureStorage.contains(saltKey(slot)) && secureStorage.contains(hashKey(slot))

    /** Returns the Argon2id key material for [slot], for immediate use in vault key derivation. */
    fun setPin(pin: CharArray, slot: PinSlot): ByteArray {
        val salt = hasher.newSalt()
        val hash = hasher.hash(pin, salt)
        secureStorage.putBytes(saltKey(slot), salt)
        secureStorage.putBytes(hashKey(slot), hash)
        return hash
    }

    fun clearPin(slot: PinSlot) {
        secureStorage.remove(saltKey(slot))
        secureStorage.remove(hashKey(slot))
    }

    /** Checks the entered PIN against both the real and panic slots (order: real, then panic). */
    fun verify(pin: CharArray): PinVerificationResult {
        for (slot in listOf(PinSlot.REAL, PinSlot.PANIC)) {
            if (!isConfigured(slot)) continue
            val salt = secureStorage.getBytes(saltKey(slot)) ?: continue
            val expectedHash = secureStorage.getBytes(hashKey(slot)) ?: continue
            val candidateHash = hasher.hash(pin, salt)
            if (MessageDigest.isEqual(candidateHash, expectedHash)) {
                return PinVerificationResult.Match(slot, candidateHash)
            }
        }
        return PinVerificationResult.NoMatch
    }

    private fun saltKey(slot: PinSlot) = when (slot) {
        PinSlot.REAL -> SecureStorage.KEY_PIN_SALT_REAL
        PinSlot.PANIC -> SecureStorage.KEY_PIN_SALT_PANIC
    }

    private fun hashKey(slot: PinSlot) = when (slot) {
        PinSlot.REAL -> SecureStorage.KEY_PIN_HASH_REAL
        PinSlot.PANIC -> SecureStorage.KEY_PIN_HASH_PANIC
    }
}
