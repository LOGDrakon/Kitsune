package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A closed chapter of the rolling summary — memory level 2, hierarchical form (FEATURES.md section 4).
 *
 * Before this existed, `ChatEntity.summary` was a single prose blob re-compacted onto itself every
 * time it crossed [com.kitsune.core.memory.summarization.SummarizationConfig.META_SUMMARY_TRIGGER_CHARS].
 * The opening of a long story was therefore paraphrased again on every crossing, losing a little
 * each pass — the flat-compaction limitation recorded in IDEAS.md since 2026-07-03. A chapter here
 * is compacted **exactly once, ever**, and then frozen.
 *
 * [fromCreatedAt]/[throughCreatedAt] are message `createdAt` bounds, in the same coordinate space as
 * `ChatEntity.summarizedThroughCreatedAt`, `KeyMomentEntity.anchorCreatedAt` and the `batch-<ts>`
 * fragment source keys. That provenance is what lets a rewind delete only the chapters it actually
 * invalidates, instead of wiping the entire summary as it had to when the summary was one opaque
 * string.
 */
@Entity(
    tableName = "story_chapters",
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
data class StoryChapterEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    /** 1-based position in the chat's chapter sequence. */
    val chapterIndex: Int,
    /** Short evocative title produced when the chapter was closed; falls back to the opening of the
     *  summary if the model's response could not be parsed. */
    val title: String,
    val summary: String,
    val fromCreatedAt: Long,
    val throughCreatedAt: Long,
    val createdAt: Long
)
