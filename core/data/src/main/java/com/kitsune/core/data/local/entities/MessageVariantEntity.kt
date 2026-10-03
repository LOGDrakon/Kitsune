package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One generated version of an assistant message (2026-08-25).
 *
 * ## Why regenerating used to destroy
 *
 * `regenerateLastResponse` deleted the previous reply before asking for a new one, so there was never
 * anything to compare: if the second attempt was worse, the first was gone. Every serious roleplay
 * client keeps them and lets you page between — it is the single most-used control in the category,
 * and its absence is the kind of thing the audience this app is courting notices in the first ten
 * minutes.
 *
 * ## Why a separate table
 *
 * `MessageEntity` is the most widely read row in the schema: rewind, the four memory layers, the
 * novel export, the audit log and the chat list preview all walk it, several of them with their own
 * filters and cursors. Adding grouping columns there would put a new invariant in front of all of
 * them at once. Variants sit beside it instead, and `messages.content` keeps meaning exactly what it
 * always meant: **the version currently in the story**. Nothing that reads messages needs to change.
 *
 * Every version lives here, including the one currently selected — so `variants.size` is the count
 * the UI shows, and switching is a copy from here into the message.
 */
@Entity(
    tableName = "message_variants",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageId")]
)
data class MessageVariantEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val content: String,
    val createdAt: Long
)
