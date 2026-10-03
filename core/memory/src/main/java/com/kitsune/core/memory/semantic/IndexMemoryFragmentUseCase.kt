package com.kitsune.core.memory.semantic

import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.repository.MemoryFragmentRepository
import java.util.UUID
import javax.inject.Inject

class IndexMemoryFragmentUseCase @Inject constructor(
    private val memoryFragmentRepository: MemoryFragmentRepository,
    private val remoteTextEmbedder: RemoteTextEmbedder,
    private val embeddingCache: EmbeddingCache
) {
    suspend operator fun invoke(chatId: String, sourceType: MemoryFragmentSource, sourceKey: String, text: String) {
        if (text.isBlank()) return

        val embedding = remoteTextEmbedder.embed(text).getOrNull() ?: return

        val existing = memoryFragmentRepository.findBySourceKey(chatId, sourceType, sourceKey)
        val id = existing?.id ?: UUID.randomUUID().toString()
        memoryFragmentRepository.upsert(
            MemoryFragmentEntity(
                id = id,
                chatId = chatId,
                sourceType = sourceType,
                sourceKey = sourceKey,
                text = text,
                embedding = embedding,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
        // Re-indexing keeps the fragment id and replaces its vector (a lore entry whose content was
        // enriched, for instance), so the cached vector for this id is now stale.
        embeddingCache.invalidate(id)
    }
}