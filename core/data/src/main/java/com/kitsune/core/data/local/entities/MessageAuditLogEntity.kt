package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Strictly local, append-only record of every message's content exactly as first written —
 * separate from the editable [MessageEntity] table it mirrors, and deliberately carries no foreign
 * key to [ChatEntity]/[MessageEntity]: normal edit ("Modifier"), regenerate, single-message
 * delete/rewind, and whole-chat deletion never touch this table, so none of them can erase the
 * record of what was actually generated (demande explicite — a user who generated content that
 * would get them banned could otherwise simply edit/delete it before a report or ban appeal is
 * reviewed, making the report look unfounded).
 *
 * Never transmitted anywhere automatically. Wiped only by a full local data wipe (account
 * deletion deletes the whole encrypted database file, see `SettingsViewModel.wipeLocalDataAndLogout`)
 * — the user's right to erase their data is unaffected, this only prevents *partial*,
 * accountability-defeating edits of a still-live conversation. See `BuildBugReportUseCase`, which
 * sources its conversation transcript from here instead of the live (editable) message table.
 */
@Entity(tableName = "message_audit_log", indices = [Index("chatId")])
data class MessageAuditLogEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    /** The [MessageEntity.id] this audit row was captured from — informational only, not a FK
     * (the original row may since have been edited or deleted). */
    val messageId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Long
)
