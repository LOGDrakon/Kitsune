package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MomentType {
    FIRST_MEETING,
    CONFESSION,
    BREAKUP,
    NSFW_SCENE,
    PLOT_TWIST,
    EMOTIONAL_PEAK,
    CONFLICT,
    RECONCILIATION,
    CUSTOM
}

enum class StoryMood {
    TENDER,
    ROMANTIC,
    DRAMATIC,
    HUMOROUS,
    DARK,
    TENSE,
    MELANCHOLIC,
    EXCITING,
    NEUTRAL
}

@Entity(
    tableName = "key_moments",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId")]
)
data class KeyMomentEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val messageId: String?,
    val momentType: MomentType,
    val mood: StoryMood,
    val title: String,
    val summary: String,
    val snippets: String,
    val isAutoDetected: Boolean,
    val createdAt: Long,
    val momentOrder: Int,

    /** `createdAt` of the last message in the batch this moment was extracted from — the sort key
     * for the chronological ledger (`BuildStoryChronologyUseCase`).
     *
     * Deliberately a real message timestamp rather than a second monotone counter alongside
     * [momentOrder]: it is directly comparable with `lore_entries.anchorCreatedAt`,
     * `chats.summarizedThroughCreatedAt`, the `batch-<ts>`/`chunk-<ts>` fragment source keys and
     * the story-chapter ranges, so rewind, pruning and chaptering all share one coordinate system.
     *
     * `0` on rows written before migration v27→v28; they tie and fall back to [momentOrder], which
     * places them first — correct, since they are by construction the oldest. No backfill needed. */
    val anchorCreatedAt: Long = 0L,

    /** The chat's `storyTimeAnchor` as it read when this moment was extracted — the in-fiction
     * clock reading, not a real-world date. Free text and possibly blank, exactly like the anchor
     * itself; used only as a human/model-readable label next to the ordered position. */
    val storyTimeLabel: String = ""
)