package com.kitsune.core.memory.semantic

import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.repository.MemoryFragmentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val CHAT_ID = "chat-1"

private val fixedEmbedding = listOf(0.1f, 0.2f, 0.3f)

class IndexMemoryFragmentUseCaseTest {

    private val memoryFragmentRepository = mockk<MemoryFragmentRepository>()
    private val remoteTextEmbedder = mockk<RemoteTextEmbedder>()

    private val embeddingCache = EmbeddingCache()

    private val useCase = IndexMemoryFragmentUseCase(memoryFragmentRepository, remoteTextEmbedder, embeddingCache)

    @Test
    fun `does nothing for blank text`() = runTest {
        useCase(CHAT_ID, MemoryFragmentSource.LORE_ENTRY, "entry-1", "   ")

        coVerify(exactly = 0) { memoryFragmentRepository.upsert(any()) }
    }

    @Test
    fun `does nothing when the embedding call fails`() = runTest {
        coEvery { remoteTextEmbedder.embed(any()) } returns Result.failure(RuntimeException("API down"))

        useCase(CHAT_ID, MemoryFragmentSource.LORE_ENTRY, "entry-1", "Aria: a tall mercenary")

        coVerify(exactly = 0) { memoryFragmentRepository.findBySourceKey(any(), any(), any()) }
        coVerify(exactly = 0) { memoryFragmentRepository.upsert(any()) }
    }

    @Test
    fun `creates a new fragment with a fresh embedding when none exists yet`() = runTest {
        coEvery { remoteTextEmbedder.embed(any()) } returns Result.success(fixedEmbedding)
        coEvery { memoryFragmentRepository.findBySourceKey(CHAT_ID, MemoryFragmentSource.LORE_ENTRY, "entry-1") } returns null
        coEvery { memoryFragmentRepository.upsert(any()) } returns Unit

        useCase(CHAT_ID, MemoryFragmentSource.LORE_ENTRY, "entry-1", "Aria: a tall mercenary")

        coVerify {
            memoryFragmentRepository.upsert(
                withArg { fragment ->
                    assertEquals(CHAT_ID, fragment.chatId)
                    assertEquals(MemoryFragmentSource.LORE_ENTRY, fragment.sourceType)
                    assertEquals("entry-1", fragment.sourceKey)
                    assertEquals("Aria: a tall mercenary", fragment.text)
                    assertEquals(fixedEmbedding, fragment.embedding)
                }
            )
        }
    }

    @Test
    fun `updates the existing fragment in place instead of duplicating`() = runTest {
        val existing = MemoryFragmentEntity(
            id = "fragment-1",
            chatId = CHAT_ID,
            sourceType = MemoryFragmentSource.LORE_ENTRY,
            sourceKey = "entry-1",
            text = "old text",
            embedding = listOf(0.9f, 0.8f, 0.7f),
            createdAt = 100L
        )
        coEvery { remoteTextEmbedder.embed(any()) } returns Result.success(fixedEmbedding)
        coEvery { memoryFragmentRepository.findBySourceKey(CHAT_ID, MemoryFragmentSource.LORE_ENTRY, "entry-1") } returns existing
        coEvery { memoryFragmentRepository.upsert(any()) } returns Unit

        useCase(CHAT_ID, MemoryFragmentSource.LORE_ENTRY, "entry-1", "new text")

        coVerify {
            memoryFragmentRepository.upsert(
                withArg { fragment ->
                    assertEquals("fragment-1", fragment.id)
                    assertEquals(100L, fragment.createdAt)
                    assertEquals("new text", fragment.text)
                    assertEquals(fixedEmbedding, fragment.embedding)
                }
            )
        }
    }
}
