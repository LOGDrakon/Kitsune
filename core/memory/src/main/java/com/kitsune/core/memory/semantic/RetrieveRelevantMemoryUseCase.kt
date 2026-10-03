package com.kitsune.core.memory.semantic

import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.repository.MemoryFragmentRepository
import javax.inject.Inject

class RetrieveRelevantMemoryUseCase @Inject constructor(
    private val memoryFragmentRepository: MemoryFragmentRepository,
    private val remoteTextEmbedder: RemoteTextEmbedder,
    private val embeddingCache: EmbeddingCache
) {
    suspend operator fun invoke(chatId: String, query: String, topK: Int = DEFAULT_TOP_K): List<String> {
        if (query.isBlank()) return emptyList()

        val fragments = memoryFragmentRepository.getByChat(chatId)
        if (fragments.isEmpty()) return emptyList()

        val queryEmbedding = remoteTextEmbedder.embed(query).getOrNull() ?: return emptyList()
        return rank(fragments, queryEmbedding, topK)
    }

    /**
     * Scoring half of [invoke], split out so `BuildTurnMemoryUseCase` can reuse the fragment list
     * and query embedding it already loaded for lore ranking instead of fetching and embedding a
     * second time.
     *
     * @param excludeChunksFromCreatedAt drops [MemoryFragmentSource.MESSAGE_CHUNK] fragments at or
     *   after this message timestamp — the messages they contain are already being sent verbatim in
     *   the raw window, so retrieving them again wastes top-K slots on text the model can already
     *   see. Only MESSAGE_CHUNK can straddle the window: SUMMARY_BATCH fragments exist only for
     *   messages that have already been folded out of it.
     */
    fun rank(
        fragments: List<MemoryFragmentEntity>,
        queryEmbedding: List<Float>,
        topK: Int = DEFAULT_TOP_K,
        excludeChunksFromCreatedAt: Long? = null
    ): List<String> {
        if (fragments.isEmpty() || queryEmbedding.isEmpty()) return emptyList()

        val queryVector = embeddingCache.build(queryEmbedding)
        return fragments
            .filterNot { it.isInRawWindow(excludeChunksFromCreatedAt) }
            .map { it to embeddingCache.similarity(queryVector, embeddingCache.get(it.id, it.embedding)) }
            .filter { (_, score) -> score >= MIN_SIMILARITY }
            .sortedByDescending { (_, score) -> score }
            .take(topK)
            .map { (fragment, _) -> fragment.text }
    }

    private fun MemoryFragmentEntity.isInRawWindow(threshold: Long?): Boolean {
        if (threshold == null || sourceType != MemoryFragmentSource.MESSAGE_CHUNK) return false
        val timestamp = sourceKey.removePrefix(CHUNK_PREFIX).toLongOrNull() ?: return false
        return timestamp >= threshold
    }

    companion object {
        const val DEFAULT_TOP_K = 6

        /** Below this cosine similarity, a fragment is treated as noise rather than genuinely relevant. */
        const val MIN_SIMILARITY = 0.12f

        private const val CHUNK_PREFIX = "chunk-"
    }
}
