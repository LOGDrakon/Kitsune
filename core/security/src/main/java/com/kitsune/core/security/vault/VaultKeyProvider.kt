package com.kitsune.core.security.vault

import androidx.fragment.app.FragmentActivity
import com.kitsune.core.security.model.PinSlot
import com.kitsune.core.security.model.VaultSecurityMode

/**
 * Produces the SQLCipher database passphrase. Which factor(s) are required is governed by the
 * current [VaultSecurityMode] (default: both biometric/device-credential AND the app PIN — see
 * FEATURES.md section 2). Changing the mode genuinely changes the derived passphrase (see
 * [changeSecurityMode]), it is not just a UI-level gate.
 */
interface VaultKeyProvider {
    suspend fun isInitialized(): Boolean

    suspend fun getSecurityMode(): VaultSecurityMode

    /** First-run setup: sets the [PinSlot.REAL] PIN, generates+wraps a new random database key,
     * and starts the vault in [VaultSecurityMode.ALL]. */
    suspend fun initialize(activity: FragmentActivity, pin: CharArray): VaultUnlockResult

    /** Verifies against whatever [getSecurityMode] currently requires and, on match, unwraps the
     * database key. [pin] is ignored when the current mode is [VaultSecurityMode.BIOMETRIC_ONLY]. */
    suspend fun unlock(activity: FragmentActivity, pin: CharArray): VaultUnlockResult

    /** Configures the optional panic PIN (see [PinSlot.PANIC]); requires the vault to already be
     * initialized and the current mode to actually use a PIN. Returns both the old (if any) and
     * new decoy-notes passphrase so the caller can rekey the decoy database — see [SetPanicPinResult]. */
    suspend fun setPanicPin(pin: CharArray): SetPanicPinResult

    /**
     * Step 1 of a security mode change: re-authenticates against the *current* mode's full
     * requirements (proving control of a factor is required before it can be dropped) and derives
     * the passphrase [targetMode] would use. Persists nothing — the vault is still on the previous
     * mode/passphrase until the caller rekeys the database and calls [commitSecurityMode].
     *
     * [currentPin] is required whenever the current mode uses a PIN (`ALL`, `PIN_ONLY`).
     * [newPin] is required only when moving into a PIN-requiring mode from [VaultSecurityMode.BIOMETRIC_ONLY],
     * where any previous PIN can no longer be assumed remembered/valid and a fresh one is set.
     * Moving into a mode that requires biometrics always triggers a fresh biometric prompt, both
     * to unwrap the database key and to confirm biometrics are actually usable on this device
     * before relying on them.
     */
    suspend fun prepareSecurityModeChange(
        activity: FragmentActivity,
        targetMode: VaultSecurityMode,
        currentPin: CharArray? = null,
        newPin: CharArray? = null
    ): VaultModeChangeResult

    /**
     * Step 2: call only after the database has been successfully rekeyed with the passphrase from
     * [prepareSecurityModeChange] — persists [targetMode] as current and cleans up any storage
     * (e.g. the software-held database key used by [VaultSecurityMode.PIN_ONLY]) no longer needed
     * outside it.
     */
    suspend fun commitSecurityMode(targetMode: VaultSecurityMode)
}
