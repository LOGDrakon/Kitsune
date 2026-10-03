package com.kitsune.core.memory.semantic

import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.repository.MemoryFragmentRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHAT_ID = "chat-1"

private val queryEmbedding = listOf(0.1f, 0.2f, 0.3f)

private fun fragment(id: String, text: String, embedding: List<Float> = queryEmbedding) = MemoryFragmentEntity(
    id = id,
    chatId = CHAT_ID,
    sourceType = MemoryFragmentSource.SUMMARY_BATCH,
    sourceKey = id,
    text = text,
    embedding = embedding,
    createdAt = 0L
)

class RetrieveRelevantMemoryUseCaseTest {

    private val memoryFragmentRepository = mockk<MemoryFragmentRepository>()
    private val remoteTextEmbedder = mockk<RemoteTextEmbedder>()

    private val embeddingCache = EmbeddingCache()

    private val useCase = RetrieveRelevantMemoryUseCase(memoryFragmentRepository, remoteTextEmbedder, embeddingCache)

    @Test
    fun `returns empty list for a blank query`() = runTest {
        val result = useCase(CHAT_ID, "   ")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty list when there are no indexed fragments`() = runTest {
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns emptyList()

        val result = useCase(CHAT_ID, "Where is Aria?")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty list when the embedding call fails`() = runTest {
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns listOf(
            fragment("f1", "Aria the mercenary carries a silver sword")
        )
        coEvery { remoteTextEmbedder.embed(any()) } returns Result.failure(RuntimeException("API down"))

        val result = useCase(CHAT_ID, "Tell me about Aria's sword")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns the most similar fragment first`() = runTest {
        coEvery { remoteTextEmbedder.embed(any()) } returns Result.success(queryEmbedding)
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns listOf(
            fragment("f1", "Aria the mercenary carries a silver sword and lives in the northern forest", queryEmbedding),
            fragment("f2", "The bakery on Main Street sells fresh croissants every morning", listOf(0f, 0f, 0f))
        )

        val result = useCase(CHAT_ID, "Tell me about Aria's sword", topK = 1)

        assertEquals(listOf("Aria the mercenary carries a silver sword and lives in the northern forest"), result)
    }

    @Test
    fun `never returns more than topK fragments`() = runTest {
        coEvery { remoteTextEmbedder.embed(any()) } returns Result.success(queryEmbedding)
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns (1..10).map {
            fragment("f$it", "Aria the mercenary silver sword forest story chapter $it", queryEmbedding)
        }

        val result = useCase(CHAT_ID, "Aria mercenary silver sword forest", topK = 3)

        assertEquals(3, result.size)
    }

    @Test
    fun `excludes fragments that are not actually relevant to the query`() = runTest {
        coEvery { remoteTextEmbedder.embed(any()) } returns Result.success(queryEmbedding)
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns listOf(
            fragment("f1", "The quarterly financial report shows a decline in overseas shipping revenue", listOf(0f, 0f, 0f))
        )

        val result = useCase(CHAT_ID, "What color are Aria's eyes?")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `default topK is 6`() = runTest {
        coEvery { remoteTextEmbedder.embed(any()) } returns Result.success(queryEmbedding)
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns (1..10).map {
            fragment("f$it", "Aria the mercenary silver sword forest story chapter $it", queryEmbedding)
        }

        val result = useCase(CHAT_ID, "Aria mercenary silver sword forest")

        assertEquals(6, result.size)
    }

    @Test
    fun `drops message chunks already visible verbatim in the raw window`() {
        // Retrieving a chunk the model can already read wastes a top-K slot on duplicated text.
        val oldChunk = chunkFragment("old", createdAt = 100)
        val visibleChunk = chunkFragment("visible", createdAt = 500)

        val result = useCase.rank(
            fragments = listOf(oldChunk, visibleChunk),
            queryEmbedding = queryEmbedding,
            excludeChunksFromCreatedAt = 500
        )

        assertEquals(listOf("chunk old"), result)
    }

    @Test
    fun `keeps summary batches at the same timestamp as the window boundary`() {
        // Only MESSAGE_CHUNK can straddle the window: a SUMMARY_BATCH only exists for messages
        // already folded out of it, so it must never be filtered.
        val batch = fragment("batch-500", "folded batch")

        val result = useCase.rank(
            fragments = listOf(batch),
            queryEmbedding = queryEmbedding,
            excludeChunksFromCreatedAt = 500
        )

        assertEquals(listOf("folded batch"), result)
    }

    @Test
    fun `keeps every chunk when no raw window boundary is given`() {
        val result = useCase.rank(
            fragments = listOf(chunkFragment("a", 100), chunkFragment("b", 900)),
            queryEmbedding = queryEmbedding,
            excludeChunksFromCreatedAt = null
        )

        assertEquals(2, result.size)
    }

    private fun chunkFragment(id: String, createdAt: Long) = MemoryFragmentEntity(
        id = id,
        chatId = CHAT_ID,
        sourceType = MemoryFragmentSource.MESSAGE_CHUNK,
        sourceKey = "chunk-$createdAt",
        text = "chunk $id",
        embedding = queryEmbedding,
        createdAt = createdAt
    )
}
