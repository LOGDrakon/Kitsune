package com.kitsune.core.security.vault

/**
 * Result of [VaultKeyProvider.setPanicPin]. [oldPassphrase] is null iff no panic PIN was
 * previously configured (true first-time setup) — nothing to rekey. Non-null means an existing
 * panic PIN is being replaced; the caller must rekey the decoy notes database from
 * [oldPassphrase] to [newPassphrase] (if that database's file actually exists yet — a panic PIN
 * can be configured without ever having been used to enter the decoy notes app) before existing
 * decoy notes remain readable.
 */
data class SetPanicPinResult(val oldPassphrase: ByteArray?, val newPassphrase: ByteArray)
