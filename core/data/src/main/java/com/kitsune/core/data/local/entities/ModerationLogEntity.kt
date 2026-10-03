package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Strictly local record of a moderation block (FEATURES.md section 3) — lives only in the
 * SQLCipher-encrypted database and is never transmitted anywhere. Stores the flagged category and
 * the filter's own (generic) match reason, never the user's raw message content.
 */
@Entity(tableName = "moderation_logs")
data class ModerationLogEntity(
    @PrimaryKey val id: String,
    val chatId: String?,
    val category: String,
    val reason: String,
    val createdAt: Long
)
