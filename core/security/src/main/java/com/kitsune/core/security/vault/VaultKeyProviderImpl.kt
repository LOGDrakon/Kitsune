package com.kitsune.core.security.vault

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.kitsune.core.common.coroutines.DispatcherProvider
import com.kitsune.core.security.biometric.BiometricAuthManager
import com.kitsune.core.security.crypto.Hkdf
import com.kitsune.core.security.decoy.DecoyNotesKeyProvider
import com.kitsune.core.security.keystore.KeystoreManager
import com.kitsune.core.security.model.BiometricAuthResult
import com.kitsune.core.security.model.PinSlot
import com.kitsune.core.security.model.PinVerificationResult
import com.kitsune.core.security.model.VaultSecurityMode
import com.kitsune.core.security.pin.PinCredentialManager
import com.kitsune.core.security.storage.SecureStorage
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.inject.Inject

private const val DB_KEY_LENGTH_BYTES = 32

// Distinct per mode by design: even though the underlying secrets (DB key, PIN hash) don't change
// across a mode switch, the derived passphrase must — collapsing two modes onto the same info
// string would mean a passphrase computed under a dropped factor's rules still worked.
private const val HKDF_INFO_ALL = "kitsune-vault-passphrase-v1" // unchanged from before VaultSecurityMode existed — existing installs' databases are keyed with this exact string, do not rename.
private const val HKDF_INFO_PIN_ONLY = "kitsune-vault-passphrase-pin-only-v1"
private const val HKDF_INFO_BIOMETRIC_ONLY = "kitsune-vault-passphrase-biometric-only-v1"

class VaultKeyProviderImpl @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val biometricAuthManager: BiometricAuthManager,
    private val pinCredentialManager: PinCredentialManager,
    private val secureStorage: SecureStorage,
    private val decoyNotesKeyProvider: DecoyNotesKeyProvider,
    private val dispatchers: DispatcherProvider
) : VaultKeyProvider {

    override suspend fun isInitialized(): Boolean = withContext(dispatchers.default) {
        secureStorage.contains(SecureStorage.KEY_WRAPPED_DB_KEY) && pinCredentialManager.isConfigured(PinSlot.REAL)
    }

    override suspend fun getSecurityMode(): VaultSecurityMode = withContext(dispatchers.default) {
        secureStorage.getString(SecureStorage.KEY_VAULT_SECURITY_MODE)
            ?.let { runCatching { VaultSecurityMode.valueOf(it) }.getOrNull() }
            ?: VaultSecurityMode.ALL
    }

    override suspend fun initialize(activity: FragmentActivity, pin: CharArray): VaultUnlockResult {
        val pinKeyMaterial = pinCredentialManager.setPin(pin, PinSlot.REAL)

        val cipher = keystoreManager.newEncryptCipher()
        val iv = cipher.iv

        val randomDbKey = ByteArray(DB_KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        return when (val authorized = authorize(activity, "Protéger le coffre Kitsune", cipher)) {
            is Authorization.Denied -> authorized.result
            is Authorization.Granted -> {
                val ciphertext = authorized.cipher.doFinal(randomDbKey)
                secureStorage.putBytes(SecureStorage.KEY_WRAPPED_DB_KEY, iv + ciphertext)
                secureStorage.putString(SecureStorage.KEY_VAULT_SECURITY_MODE, VaultSecurityMode.ALL.name)

                val passphrase = Hkdf.deriveKey(randomDbKey + pinKeyMaterial, HKDF_INFO_ALL.toByteArray())
                randomDbKey.fill(0)
                VaultUnlockResult.Unlocked(passphrase, PinSlot.REAL)
            }
        }
    }

    override suspend fun unlock(activity: FragmentActivity, pin: CharArray): VaultUnlockResult {
        return when (getSecurityMode()) {
            VaultSecurityMode.ALL -> unlockAll(activity, pin)
            VaultSecurityMode.PIN_ONLY -> unlockPinOnly(pin)
            VaultSecurityMode.BIOMETRIC_ONLY -> unlockBiometricOnly(activity)
        }
    }

    private suspend fun unlockAll(activity: FragmentActivity, pin: CharArray): VaultUnlockResult {
        val match = pinCredentialManager.verify(pin) as? PinVerificationResult.Match
            ?: return VaultUnlockResult.WrongPin

        // The panic PIN never touches the real Keystore-wrapped key: it must not be able to
        // derive a working passphrase for the real database under any circumstance. Callers
        // are expected to branch on `slot == PANIC` and present decoy content instead.
        if (match.slot == PinSlot.PANIC) {
            return VaultUnlockResult.Unlocked(decoyNotesKeyProvider.derivePassphrase(match.keyMaterial), PinSlot.PANIC)
        }

        val randomDbKey = when (val outcome = unwrapDbKeyViaBiometric(activity, "Déverrouiller Kitsune")) {
            is DbKeyOutcome.Success -> outcome.dbKey
            DbKeyOutcome.NotInitialized -> return VaultUnlockResult.NotInitialized
            is DbKeyOutcome.BiometricFailed -> return VaultUnlockResult.BiometricFailed(outcome.errorCode, outcome.message)
            DbKeyOutcome.BiometricCancelled -> return VaultUnlockResult.BiometricCancelled
        }
        val passphrase = Hkdf.deriveKey(randomDbKey + match.keyMaterial, HKDF_INFO_ALL.toByteArray())
        randomDbKey.fill(0)
        return VaultUnlockResult.Unlocked(passphrase, PinSlot.REAL)
    }

    private fun unlockPinOnly(pin: CharArray): VaultUnlockResult {
        val match = pinCredentialManager.verify(pin) as? PinVerificationResult.Match
            ?: return VaultUnlockResult.WrongPin

        if (match.slot == PinSlot.PANIC) {
            return VaultUnlockResult.Unlocked(decoyNotesKeyProvider.derivePassphrase(match.keyMaterial), PinSlot.PANIC)
        }

        val randomDbKey = secureStorage.getBytes(SecureStorage.KEY_DB_KEY_SOFTWARE)
            ?: return VaultUnlockResult.NotInitialized
        val passphrase = Hkdf.deriveKey(randomDbKey + match.keyMaterial, HKDF_INFO_PIN_ONLY.toByteArray())
        randomDbKey.fill(0)
        return VaultUnlockResult.Unlocked(passphrase, PinSlot.REAL)
    }

    private suspend fun unlockBiometricOnly(activity: FragmentActivity): VaultUnlockResult {
        val randomDbKey = when (val outcome = unwrapDbKeyViaBiometric(activity, "Déverrouiller Kitsune")) {
            is DbKeyOutcome.Success -> outcome.dbKey
            DbKeyOutcome.NotInitialized -> return VaultUnlockResult.NotInitialized
            is DbKeyOutcome.BiometricFailed -> return VaultUnlockResult.BiometricFailed(outcome.errorCode, outcome.message)
            DbKeyOutcome.BiometricCancelled -> return VaultUnlockResult.BiometricCancelled
        }
        val passphrase = Hkdf.deriveKey(randomDbKey, HKDF_INFO_BIOMETRIC_ONLY.toByteArray())
        randomDbKey.fill(0)
        return VaultUnlockResult.Unlocked(passphrase, PinSlot.REAL)
    }

    override suspend fun setPanicPin(pin: CharArray): SetPanicPinResult {
        check(isInitialized()) { "Cannot set a panic PIN before the vault is initialized" }
        check(getSecurityMode() != VaultSecurityMode.BIOMETRIC_ONLY) {
            "Panic PIN requires PIN unlock to be enabled — there is no PIN entry point to trigger it in BIOMETRIC_ONLY mode"
        }
        // Pentest finding (see BUGS.md): PinCredentialManager.verify() checks PinSlot.REAL before
        // PANIC, so if the panic PIN were ever set equal to the real PIN, typing it under duress
        // would silently unlock the REAL vault instead of the decoy — exactly the scenario this
        // feature exists to prevent, with no warning to the user. Reject that here, at setup time.
        // A candidate that matches the CURRENT panic PIN (re-submitting the same one) is fine.
        val candidateMatch = pinCredentialManager.verify(pin)
        check(candidateMatch !is PinVerificationResult.Match || candidateMatch.slot != PinSlot.REAL) {
            "Le PIN de panique ne peut pas etre identique au PIN reel."
        }

        // If a panic PIN was already configured, its stored Argon2id hash IS the exact key
        // material a verify() of that old PIN would produce (PinCredentialManager.setPin returns
        // the same hash it persists) — reading it here, before it's overwritten below, lets us
        // derive the OLD decoy passphrase without ever asking the user to re-type their old panic
        // PIN. Null means this is true first-time setup — nothing to rekey.
        val oldPassphrase = secureStorage.getBytes(SecureStorage.KEY_PIN_HASH_PANIC)
            ?.let { decoyNotesKeyProvider.derivePassphrase(it) }

        val newMaterial = pinCredentialManager.setPin(pin, PinSlot.PANIC)
        val newPassphrase = decoyNotesKeyProvider.derivePassphrase(newMaterial)

        return SetPanicPinResult(oldPassphrase, newPassphrase)
    }

    override suspend fun prepareSecurityModeChange(
        activity: FragmentActivity,
        targetMode: VaultSecurityMode,
        currentPin: CharArray?,
        newPin: CharArray?
    ): VaultModeChangeResult {
        val currentMode = getSecurityMode()

        // Step A: satisfy the CURRENT mode's full requirements — this is what authorizes the
        // change at all, regardless of which mode we're moving to.
        val currentPinMaterial: ByteArray? = if (currentMode != VaultSecurityMode.BIOMETRIC_ONLY) {
            val pin = currentPin ?: return VaultModeChangeResult.WrongPin
            val match = pinCredentialManager.verify(pin) as? PinVerificationResult.Match
            if (match == null || match.slot != PinSlot.REAL) return VaultModeChangeResult.WrongPin
            match.keyMaterial
        } else {
            null
        }

        // While we're at it, use a fresh biometric prompt (when either the current or target mode
        // needs one) to confirm biometrics are actually usable on this device *before* relying on
        // them — better to fail here than discover it locked out at the next cold start.
        val needsBiometricPrompt = currentMode != VaultSecurityMode.PIN_ONLY || targetMode != VaultSecurityMode.PIN_ONLY
        val dbKey: ByteArray = if (needsBiometricPrompt) {
            when (val outcome = unwrapDbKeyViaBiometric(activity, "Confirmer le changement de sécurité")) {
                is DbKeyOutcome.Success -> outcome.dbKey
                DbKeyOutcome.NotInitialized -> return VaultModeChangeResult.BiometricCancelled
                is DbKeyOutcome.BiometricFailed -> return VaultModeChangeResult.BiometricFailed(outcome.errorCode, outcome.message)
                DbKeyOutcome.BiometricCancelled -> return VaultModeChangeResult.BiometricCancelled
            }
        } else {
            secureStorage.getBytes(SecureStorage.KEY_DB_KEY_SOFTWARE) ?: return VaultModeChangeResult.BiometricCancelled
        }

        // Step B: derive the passphrase the TARGET mode will use.
        val newPassphrase = when (targetMode) {
            VaultSecurityMode.ALL -> {
                val pinMaterial = currentPinMaterial ?: run {
                    val fresh = newPin ?: return VaultModeChangeResult.MissingNewPin
                    pinCredentialManager.setPin(fresh, PinSlot.REAL)
                }
                Hkdf.deriveKey(dbKey + pinMaterial, HKDF_INFO_ALL.toByteArray())
            }
            VaultSecurityMode.PIN_ONLY -> {
                val pinMaterial = currentPinMaterial ?: run {
                    val fresh = newPin ?: return VaultModeChangeResult.MissingNewPin
                    pinCredentialManager.setPin(fresh, PinSlot.REAL)
                }
                secureStorage.putBytes(SecureStorage.KEY_DB_KEY_SOFTWARE, dbKey)
                Hkdf.deriveKey(dbKey + pinMaterial, HKDF_INFO_PIN_ONLY.toByteArray())
            }
            VaultSecurityMode.BIOMETRIC_ONLY -> Hkdf.deriveKey(dbKey, HKDF_INFO_BIOMETRIC_ONLY.toByteArray())
        }

        dbKey.fill(0)
        return VaultModeChangeResult.Prepared(newPassphrase)
    }

    override suspend fun commitSecurityMode(targetMode: VaultSecurityMode) {
        secureStorage.putString(SecureStorage.KEY_VAULT_SECURITY_MODE, targetMode.name)
        // The software-held DB key only needs to exist while PIN_ONLY is active — everywhere else
        // it would be a standing copy of the key that bypasses the biometric factor for no reason.
        if (targetMode != VaultSecurityMode.PIN_ONLY) {
            secureStorage.remove(SecureStorage.KEY_DB_KEY_SOFTWARE)
        }
    }

    private sealed interface Authorization {
        class Granted(val cipher: Cipher) : Authorization
        class Denied(val result: VaultUnlockResult) : Authorization
    }

    private suspend fun authorize(activity: FragmentActivity, title: String, cipher: Cipher): Authorization {
        return when (val result = biometricAuthManager.authenticate(activity, title, cryptoObject = BiometricPrompt.CryptoObject(cipher))) {
            is BiometricAuthResult.Success -> result.cryptoObject?.cipher?.let { Authorization.Granted(it) }
                ?: Authorization.Denied(VaultUnlockResult.BiometricFailed(-1, "No cipher returned by BiometricPrompt"))
            is BiometricAuthResult.Failed -> Authorization.Denied(VaultUnlockResult.BiometricFailed(result.errorCode, result.message))
            is BiometricAuthResult.Cancelled -> Authorization.Denied(VaultUnlockResult.BiometricCancelled)
        }
    }

    /** Outcome-agnostic result for unwrapping the Keystore-wrapped DB key, so both [VaultUnlockResult]
     * and [VaultModeChangeResult] call sites can map it to their own failure variants. */
    private sealed interface DbKeyOutcome {
        class Success(val dbKey: ByteArray) : DbKeyOutcome
        data object NotInitialized : DbKeyOutcome
        class BiometricFailed(val errorCode: Int, val message: String) : DbKeyOutcome
        data object BiometricCancelled : DbKeyOutcome
    }

    private suspend fun unwrapDbKeyViaBiometric(activity: FragmentActivity, title: String): DbKeyOutcome {
        val wrapped = secureStorage.getBytes(SecureStorage.KEY_WRAPPED_DB_KEY) ?: return DbKeyOutcome.NotInitialized
        val iv = wrapped.copyOfRange(0, KeystoreManager.IV_LENGTH_BYTES)
        val ciphertext = wrapped.copyOfRange(KeystoreManager.IV_LENGTH_BYTES, wrapped.size)

        val cipher = keystoreManager.newDecryptCipher(iv)
        return when (val authorized = authorize(activity, title, cipher)) {
            is Authorization.Denied -> when (val result = authorized.result) {
                is VaultUnlockResult.BiometricFailed -> DbKeyOutcome.BiometricFailed(result.errorCode, result.message)
                else -> DbKeyOutcome.BiometricCancelled
            }
            is Authorization.Granted -> DbKeyOutcome.Success(authorized.cipher.doFinal(ciphertext))
        }
    }
}
