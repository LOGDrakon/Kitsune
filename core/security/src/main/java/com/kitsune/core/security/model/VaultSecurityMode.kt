package com.kitsune.core.security.model

/**
 * Which factor(s) are required to derive the SQLCipher passphrase (see
 * [com.kitsune.core.security.vault.VaultKeyProvider]). At least one factor is always required —
 * there is deliberately no "no lock" mode, since this vault protects adult content the app is
 * explicitly designed to keep private (FEATURES.md section 2).
 *
 * Each mode uses a distinct HKDF info string so the derived passphrase can never accidentally
 * collide between modes even when the underlying secrets (DB key, PIN hash) are unchanged —
 * switching modes always re-derives a genuinely different passphrase and requires a real
 * `PRAGMA rekey` migration, never just a UI-level gate.
 */
enum class VaultSecurityMode {
    /** Default, most secure mode: both biometric/device-credential AND the app PIN are required. */
    ALL,

    /** Only the app PIN is required — the biometric factor is not used to derive the passphrase. */
    PIN_ONLY,

    /** Only biometric/device-credential is required. Disables the app PIN entirely, which also
     * disables the panic PIN (it has no PIN entry point to be typed into in this mode). */
    BIOMETRIC_ONLY
}
