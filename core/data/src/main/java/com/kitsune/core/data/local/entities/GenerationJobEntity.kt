package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks an asynchronous generation job started by the WorkManager scheduler.
 * The actual generated proposal(s) are stored as JSON in [resultJson]; this table only stores
 * metadata and the raw result so the UI can review it even if the app was killed.
 */
@Entity(tableName = "generation_jobs")
data class GenerationJobEntity(
    @PrimaryKey val id: String,
    val type: GenerationJobType,
    val state: GenerationJobState,
    /** Free-form description shown to the user (e.g. the persona/universe request text). */
    val description: String,
    /** Optional parent ids used when generating from a universe/chat/NPC flow. */
    val universeId: String? = null,
    val chatId: String? = null,
    val fromNpcId: String? = null,
    /** How a generated NPC should be folded into the scene once the job completes (e.g. RETROACTIVE/ARRIVAL). */
    val joinFraming: String? = null,
    /** Number of proposals originally requested (persona/universe) — kept so a failed job can be
     * retried with the exact same request instead of just re-describing it from scratch. */
    val proposalCount: Int = 1,
    /** Persona-only: the style-template hint appended to the description at creation time, if any —
     * also kept for retry, see [proposalCount]. */
    val templateStyleHint: String? = null,
    /** Serialised JSON result (modelled by `core:background`). */
    val resultJson: String? = null,
    /** Localised error message when [state] is [GenerationJobState.FAILED]. */
    val errorMessage: String? = null,
    /** [com.kitsune.core.common.generation.GenerationFailureCategory.name] when [state] is
     * [GenerationJobState.FAILED] — null for jobs that failed before this field existed, or for a
     * state other than FAILED. Stored as a plain string (like [errorMessage]) rather than a Room
     * enum column since it's optional/nullable and only ever read back through
     * [com.kitsune.core.data.local.entities.failureCategory]. */
    val errorCategory: String? = null,
    /** Whether a completion notification has already been posted for this job. */
    val notificationShown: Boolean = false,
    val createdAt: Long,
    val completedAt: Long? = null
)

/** Safely parses [GenerationJobEntity.errorCategory] back into an enum, falling back to null for
 * legacy rows (failed before this field existed) or an unrecognised value. */
fun GenerationJobEntity.failureCategory(): com.kitsune.core.common.generation.GenerationFailureCategory? =
    errorCategory?.let {
        runCatching { com.kitsune.core.common.generation.GenerationFailureCategory.valueOf(it) }.getOrNull()
    }
