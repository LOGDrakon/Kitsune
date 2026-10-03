package com.kitsune.core.memory.rewind

import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MemoryFragmentRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.StoryChapterRepository
import com.kitsune.core.memory.semantic.EmbeddingCache
import javax.inject.Inject

/**
 * Deletes one message, or a whole tail of a conversation ("revenir à ce point", requested
 * explicitly by the user), without leaving the long-term memory pipeline (FEATURES.md section 4)
 * describing events that no longer exist in the raw log:
 *
 * - The rolling summary (level 2) is AI-compacted prose — once several messages are merged into it
 *   there is no way to selectively "un-merge" just the ones being deleted. If any deleted message
 *   falls at or before [com.kitsune.core.data.local.entities.ChatEntity.summarizedThroughCreatedAt],
 *   the summary is reset entirely (and will simply regrow from the remaining messages) rather than
 *   left describing scenes that no longer happened.
 * - Semantic-memory fragments (level 4, [MemoryFragmentSource.SUMMARY_BATCH]) are keyed by the
 *   timestamp of the last message in their batch (`"batch-<createdAt>"`, see
 *   [com.kitsune.core.memory.summarization.UpdateChatSummaryUseCase]) — any batch reaching into the
 *   deleted range is purged so deleted content can't leak back into future prompts via retrieval.
 *
 * Known limitation (see IDEAS.md): structured lore entries (level 3) are not retroactively pruned —
 * there is no per-message provenance recorded for them, so an NPC introduced only in the deleted
 * range may still linger in the lore roster. Documented rather than silently "fixed" by guessing.
 */
class RewindChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val memoryFragmentRepository: MemoryFragmentRepository,
    private val storyChapterRepository: StoryChapterRepository,
    private val embeddingCache: EmbeddingCache
) {
    suspend fun deleteSingleMessage(chatId: String, message: MessageEntity) {
        deleteMessages(chatId, listOf(message))
    }

    /** Deletes [fromMessage] and every later message in the same chat. */
    suspend fun rewindTo(chatId: String, fromMessage: MessageEntity) {
        deleteMessages(chatId, messageRepository.getFrom(chatId, fromMessage.createdAt))
    }

    private suspend fun deleteMessages(chatId: String, messages: List<MessageEntity>) {
        if (messages.isEmpty()) return
        val chat = chatRepository.getById(chatId) ?: return
        val earliestDeletedAt = messages.minOf { it.createdAt }

        messages.forEach { messageRepository.delete(it) }

        // BUG-102: both resets used to be written as separate `chat.copy(...)` upserts off the same
        // stale snapshot, so when both applied the second write silently restored the summary the
        // first had just cleared. Accumulate onto one value, write once.
        var updated = chat
        if (earliestDeletedAt <= chat.summarizedThroughCreatedAt) {
            // Chapters carry message-range provenance, so only those actually reaching into the
            // deleted tail are dropped. Before they existed the whole summary had to be wiped:
            // rewinding sixty messages of a six-hundred-message story destroyed all of it.
            storyChapterRepository.deleteFrom(chatId, earliestDeletedAt)
            val lastSurviving = storyChapterRepository.getByChat(chatId).lastOrNull()
            updated = updated.copy(
                summary = "",
                summarizedThroughCreatedAt = lastSurviving?.throughCreatedAt ?: 0L,
                derivedThroughCreatedAt = lastSurviving?.throughCreatedAt ?: 0L,
                derivedRetryCount = 0
            )
        }
        if (earliestDeletedAt <= chat.lastChunkIndexedAt) {
            updated = updated.copy(lastChunkIndexedAt = 0L)
        }

        memoryFragmentRepository.getByChat(chatId)
            .filter { it.sourceType == MemoryFragmentSource.SUMMARY_BATCH || it.sourceType == MemoryFragmentSource.MESSAGE_CHUNK }
            .filter { fragment ->
                val timestamp = fragment.sourceKey.removePrefix("batch-").removePrefix("chunk-").toLongOrNull()
                timestamp != null && timestamp >= earliestDeletedAt
            }
            .forEach {
                memoryFragmentRepository.delete(it)
                embeddingCache.invalidate(it.id)
            }

        if (updated !== chat) {
            chatRepository.upsert(updated.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
