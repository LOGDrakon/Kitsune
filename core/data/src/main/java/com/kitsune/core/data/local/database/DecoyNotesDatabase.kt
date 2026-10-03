package com.kitsune.core.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kitsune.core.data.local.dao.NoteDao
import com.kitsune.core.data.local.entities.NoteEntity

/**
 * Separate, SQLCipher-encrypted Room database for the decoy notes app shown when the panic PIN is
 * entered — entirely independent from [KitsuneDatabase] (own file, own encryption key, see
 * `com.kitsune.core.security.decoy.DecoyNotesKeyProvider`), so the panic PIN can never derive the
 * real vault's passphrase under any circumstance.
 */
@Database(entities = [NoteEntity::class], version = 1, exportSchema = true)
abstract class DecoyNotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        const val DATABASE_NAME = "decoy_notes.db"
    }
}
