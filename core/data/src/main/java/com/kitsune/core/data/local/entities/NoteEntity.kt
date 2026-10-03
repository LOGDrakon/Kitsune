package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A note in the decoy notes app shown when the panic PIN is entered (see
 * `com.kitsune.core.security.decoy.DecoyNotesKeyProvider`). Lives in its own SQLCipher-encrypted
 * `DecoyNotesDatabase`, entirely independent from the real vault's `KitsuneDatabase`.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
