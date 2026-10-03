package com.kitsune.core.data.local.database

import kotlinx.coroutines.flow.StateFlow

/**
 * Mirrors [KitsuneDatabaseProvider]'s shape for the entirely independent decoy notes database.
 * Deliberately narrower — no `exportPlaintextCopy`/`importPlaintextCopy` (decoy notes don't travel
 * with account transfer) and no `runInTransaction` (not needed for a single-entity CRUD note app).
 */
interface DecoyNotesDatabaseProvider {
    fun isOpen(): Boolean

    /** Opens (creating on first ever call) the decoy notes database with [passphrase]. Suspends
     *  because a brand-new database is seeded with a few placeholder notes before returning. */
    suspend fun open(passphrase: ByteArray): DecoyNotesDatabase

    fun requireOpen(): DecoyNotesDatabase
    fun close()

    /** Re-encrypts the already-open database in place with [newPassphrase] (`PRAGMA rekey`) — used
     *  when the panic PIN is changed, see `SettingsViewModel.setPanicPin`. */
    suspend fun rekey(newPassphrase: ByteArray)

    /** Emits the current database instance, or null when not currently open. */
    val databaseState: StateFlow<DecoyNotesDatabase?>
}
