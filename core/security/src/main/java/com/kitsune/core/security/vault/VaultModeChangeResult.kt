package com.kitsune.core.security.vault

sealed interface VaultModeChangeResult {
    /**
     * Re-authentication against the current mode succeeded and [newPassphrase] was derived for
     * the target mode — nothing is persisted yet. The caller (which, unlike `core:security`, is
     * allowed to depend on `core:data`) must rekey the database with it and only then call
     * [com.kitsune.core.security.vault.VaultKeyProvider.commitSecurityMode].
     */
    class Prepared(val newPassphrase: ByteArray) : VaultModeChangeResult
    data object WrongPin : VaultModeChangeResult
    data object MissingNewPin : VaultModeChangeResult
    class BiometricFailed(val errorCode: Int, val message: String) : VaultModeChangeResult
    data object BiometricCancelled : VaultModeChangeResult
}
