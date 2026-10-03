package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** What a [MemoryFragmentEntity] was embedded from — memory level 4 (FEATURES.md section 4: "RAG local"). */
enum class MemoryFragmentSource {
    /** A batch of raw messages folded into the rolling summary (level 2) — kept verbatim here so detail lost to compression can still be retrieved. */
    SUMMARY_BATCH,

    /** A structured lore entry (level 3) — indexed so it can be retrieved even when not currently in the lore roster shown in the prompt. */
    LORE_ENTRY,

    /** A fixed-interval chunk of recent messages, indexed independently of the summary trigger for finer-grained RAG coverage. */
    MESSAGE_CHUNK,

    /** A closed story chapter (see `StoryChapterEntity`) — indexed so chapters that have scrolled
     *  past the prompt's chapter cap remain reachable by semantic retrieval.
     *
     *  Appended at the END of this enum deliberately: the column is TEXT holding `.name`, and the
     *  converter's `safeValueOf` falls back to the first constant for unknown values, so adding a
     *  value here needs no migration and an older build reading a newer row degrades sanely. */
    CHAPTER_SUMMARY
}

/**
 * A chunk of story text plus its on-device embedding, used for semantic retrieval (memory level 4:
 * "RAG local, embeddings on-device, recherche sémantique" — FEATURES.md section 4).
 *
 * [embedding] is a real neural embedding (1536-dimensional `text-embedding-3-small`, obtained
 * through the backend's `/v1/embeddings` proxy — see
 * [com.kitsune.core.memory.semantic.RemoteTextEmbedder]). The original hashed bag-of-words
 * `LocalTextEmbedder` this doc used to describe was replaced in DB v18→v19, which also purged every
 * pre-existing fragment since the two vector spaces are not comparable. The trade-off is the
 * opposite of the old one: far better semantic recall, but indexing requires network — on failure a
 * fragment is simply not stored rather than stored unembedded.
 *
 * Stored as comma-joined TEXT (`Converters.fromEmbedding`), which makes parsing rather than disk
 * the real cost at retrieval time; [com.kitsune.core.memory.semantic.EmbeddingCache] absorbs it.
 *
 * [sourceKey] is a stable id used to upsert in place instead of duplicating (the source lore entry's
 * id for [MemoryFragmentSource.LORE_ENTRY], a synthetic per-batch id for [MemoryFragmentSource.SUMMARY_BATCH]).
 */
@Entity(
    tableName = "memory_fragments",
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
data class MemoryFragmentEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val sourceType: MemoryFragmentSource,
    val sourceKey: String,
    val text: String,
    val embedding: List<Float>,
    val createdAt: Long
)
