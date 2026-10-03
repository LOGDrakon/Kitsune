package com.kitsune.core.data.local.database

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Room can't be handed a Hilt-provided singleton the usual way here: opening the database
 * requires the SQLCipher passphrase, which only exists after the user has cleared the dual
 * biometric+PIN auth in core:security. Callers open the database once post-unlock and reuse
 * the returned instance; there is no always-available `KitsuneDatabase` in the Hilt graph.
 */
interface KitsuneDatabaseProvider {
    fun isOpen(): Boolean
    fun open(passphrase: ByteArray): KitsuneDatabase
    fun requireOpen(): KitsuneDatabase
    fun close()

    /**
     * Re-encrypts the already-open database in place with [newPassphrase] (`PRAGMA rekey`), for
     * `VaultSecurityMode` changes (core:security) where the derived passphrase itself changes.
     * Requires the vault to already be unlocked. Verifies the new key actually works before
     * returning — callers must only persist the new security mode after this succeeds, since a
     * half-applied rekey with a forgotten passphrase would lock the user out permanently.
     */
    suspend fun rekey(newPassphrase: ByteArray)

    /**
     * Account-transfer export (see FEATURES.md section 2): writes a **plaintext** (unencrypted)
     * copy of the currently-open database to [destination], via SQLCipher's own
     * `sqlcipher_export` — the standard decrypt-to-plaintext-file recipe. Requires the vault to
     * already be unlocked. Callers must treat [destination] as sensitive (it's the actual data,
     * not yet re-encrypted) and delete it themselves immediately after wrapping it in a transport
     * envelope — this method does not delete it.
     */
    suspend fun exportPlaintextCopy(destination: File)

    /**
     * Account-transfer import: the reverse of [exportPlaintextCopy] — takes a **plaintext**
     * database file at [source] (already decrypted from the transport envelope by the caller) and
     * writes a freshly SQLCipher-encrypted copy, keyed with [newPassphrase], at this device's
     * normal database path. Must be called before any [open] call in this process — there must be
     * no database file already at the normal path, and nothing may currently be open. After this
     * returns, a normal [open] call with the same [newPassphrase] opens the imported data exactly
     * as if it had always lived on this device (same schema, same Room bookkeeping — a full
     * `sqlcipher_export` copy preserves everything byte-for-byte at the SQL level).
     */
    suspend fun importPlaintextCopy(source: File, newPassphrase: ByteArray)

    /**
     * Runs [block] inside a single Room database transaction: every write made through any
     * repository/DAO call inside [block] either all commit together, or none do if [block] throws
     * (BUGS.md BUG-007/BUG-015 — multi-step writes like "persist a persona, then its images" or
     * "persist an updated summary, then its lore entries" previously had no such guarantee, so a
     * failure partway through left the database in a half-written state). Callers pass repository
     * calls, not raw SQL — this only wraps Room's own transaction primitive
     * (`androidx.room.withTransaction`) so `feature:*`/`core:memory` modules don't need a direct
     * Room dependency just to get atomicity. Requires the vault to already be unlocked.
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T

    /**
     * Emits the current database instance, or null when the vault is locked/not yet unlocked.
     * Repositories observe this so their Flows stay dormant (emitting nothing) instead of
     * crashing when accessed before the vault is unlocked.
     */
    val databaseState: StateFlow<KitsuneDatabase?>
}
