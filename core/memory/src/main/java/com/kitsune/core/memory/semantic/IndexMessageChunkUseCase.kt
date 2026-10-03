package com.kitsune.core.memory.semantic

import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.storyContentOnly
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.memory.summarization.SummarizationConfig
import javax.inject.Inject

/**
 * Memory level 4 indexing on a fixed message interval, independent of the summary trigger — so a
 * long stretch of conversation is retrievable well before it is ever folded into the summary.
 *
 * BUG-103: this used to branch on `lastChunkIndexedAt == 0L` and read the first chunk via
 * `getRecent`, which returns messages **descending**. Three things went wrong at once: the indexed
 * text read backwards, `chunk.last()` was the *oldest* of the eight rather than the newest so the
 * watermark jumped backwards, and every later run used the inclusive `getFrom` and therefore
 * re-indexed the watermark message into the next chunk. A single ascending, strictly-after read
 * fixes all three, and lets a backlog be drained in one pass instead of one chunk per sent message.
 */
class IndexMessageChunkUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val indexMemoryFragmentUseCase: IndexMemoryFragmentUseCase
) {
    suspend operator fun invoke(chatId: String) {
        val chat = chatRepository.getById(chatId) ?: return

        var remaining = messageRepository.getAfter(chatId, chat.lastChunkIndexedAt)
        if (remaining.size < SummarizationConfig.CHUNK_INTERVAL) return

        var watermark = chat.lastChunkIndexedAt
        var chunksIndexed = 0

        while (remaining.size >= SummarizationConfig.CHUNK_INTERVAL && chunksIndexed < MAX_CHUNKS_PER_RUN) {
            val chunk = remaining.take(SummarizationConfig.CHUNK_INTERVAL)
            val chunkText = chunk.storyContentOnly().joinToString("\n") { "${speakerLabel(it)}: ${it.content}" }

            indexMemoryFragmentUseCase(
                chatId = chatId,
                sourceType = MemoryFragmentSource.MESSAGE_CHUNK,
                sourceKey = "chunk-${chunk.last().createdAt}",
                text = chunkText
            )

            watermark = chunk.last().createdAt
            remaining = remaining.drop(SummarizationConfig.CHUNK_INTERVAL)
            chunksIndexed++
        }

        if (watermark != chat.lastChunkIndexedAt) {
            chatRepository.upsert(
                chat.copy(lastChunkIndexedAt = watermark, updatedAt = System.currentTimeMillis())
            )
        }
    }

    private fun speakerLabel(message: MessageEntity): String = when (message.role) {
        MessageRole.USER -> "User"
        MessageRole.ASSISTANT -> "Character"
        MessageRole.SYSTEM -> "System"
        // Filtered out upstream by storyContentOnly(); labelled rather than crashed on if one ever slips through.
        MessageRole.STYLE_DIRECTIVE -> "Style directive"
    }

    private companion object {
        /** Bounds the catch-up so importing or restoring a long chat can't fire hundreds of
         *  embedding calls in one go; the remainder is picked up on the next send. */
        const val MAX_CHUNKS_PER_RUN = 5
    }
}
