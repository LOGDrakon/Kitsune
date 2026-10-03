package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,

    /**
     * A style-pivot directive dropped into the transcript at the exact point the user changed the
     * chat's writing settings (see `ChatStyleContract.buildStylePivot`). Sent to the model as an
     * inline `system` turn, but **never shown to the user and never treated as story content** —
     * it must stay out of the rendered thread, the conversation-list preview, the rolling summary,
     * lore/key-moment extraction, semantic indexing and the novel export.
     *
     * Deliberately appended last: `Converters.safeValueOf` falls back to the enum's *first* value
     * for an unrecognised string, so on an app downgrade an existing marker would be read as USER.
     * Keeping it last means no pre-existing role can ever shift onto a different constant.
     */
    STYLE_DIRECTIVE
}

/**
 * Drops [MessageRole.STYLE_DIRECTIVE] markers, which are instructions aimed at the model rather
 * than events in the story. Every memory pipeline (rolling summary, lore, key moments, semantic
 * indexing, recap) must apply this before treating a batch as narrative content — otherwise a
 * marker gets summarised, embedded and extracted as if a character had said it.
 */
fun List<MessageEntity>.storyContentOnly(): List<MessageEntity> =
    filter { it.role != MessageRole.STYLE_DIRECTIVE }

/** Local-only moderation outcome for this message (see FEATURES.md section 3). Never transmitted. */
enum class ModerationFlag {
    NONE,
    FLAGGED,
    BLOCKED
}

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Composite rather than Index("chatId"): every memory-pipeline query filters on chatId AND
    // orders/filters on createdAt (getRecent, getUnsummarized, getAfter, getMessagesUpTo), and a
    // composite index covers the chatId-only lookups as a prefix, so nothing is lost by replacing
    // the single-column one.
    indices = [Index(value = ["chatId", "createdAt"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: MessageRole,
    val content: String,
    val imageAttachmentPath: String?,
    val isEdited: Boolean = false,
    val moderationFlag: ModerationFlag = ModerationFlag.NONE,
    val tokenCount: Int?,
    val createdAt: Long
)
