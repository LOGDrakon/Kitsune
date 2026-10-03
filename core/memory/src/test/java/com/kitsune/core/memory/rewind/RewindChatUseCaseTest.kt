package com.kitsune.core.memory.rewind

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MemoryFragmentRepository
import com.kitsune.core.data.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coJustRun
import io.mockk.mockk
import com.kitsune.core.data.local.entities.StoryChapterEntity
import com.kitsune.core.data.repository.StoryChapterRepository
import com.kitsune.core.memory.semantic.EmbeddingCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val CHAT_ID = "chat-1"

class RewindChatUseCaseTest {

    private val chatRepository = mockk<ChatRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val memoryFragmentRepository = mockk<MemoryFragmentRepository>()

    private val storyChapterRepository = mockk<StoryChapterRepository>(relaxed = true)
    private val embeddingCache = EmbeddingCache()

    private val useCase = RewindChatUseCase(
        chatRepository,
        messageRepository,
        memoryFragmentRepository,
        storyChapterRepository,
        embeddingCache
    )

    private fun chat(
        summarizedThrough: Long = 0L,
        summary: String = "",
        lastChunkIndexedAt: Long = 0L
    ) = ChatEntity(
        id = CHAT_ID,
        universeId = null,
        personaId = "persona-1",
        title = "",
        mode = ChatMode.CHAT,
        summary = summary,
        summarizedThroughCreatedAt = summarizedThrough,
        lastChunkIndexedAt = lastChunkIndexedAt,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun message(createdAt: Long) = MessageEntity(
        id = "msg-$createdAt",
        chatId = CHAT_ID,
        role = MessageRole.USER,
        content = "message $createdAt",
        imageAttachmentPath = null,
        tokenCount = null,
        createdAt = createdAt
    )

    private fun batchFragment(lastCreatedAt: Long) = MemoryFragmentEntity(
        id = "fragment-$lastCreatedAt",
        chatId = CHAT_ID,
        sourceType = MemoryFragmentSource.SUMMARY_BATCH,
        sourceKey = "batch-$lastCreatedAt",
        text = "batch text",
        embedding = emptyList(),
        createdAt = lastCreatedAt
    )

    private fun chunkFragment(lastCreatedAt: Long) = MemoryFragmentEntity(
        id = "chunk-fragment-$lastCreatedAt",
        chatId = CHAT_ID,
        sourceType = MemoryFragmentSource.MESSAGE_CHUNK,
        sourceKey = "chunk-$lastCreatedAt",
        text = "chunk text",
        embedding = emptyList(),
        createdAt = lastCreatedAt
    )

    @Test
    fun `deleting a single message still-unsummarized leaves the summary untouched`() = runTest {
        val target = message(50L)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summarizedThrough = 10L)
        coJustRun { messageRepository.delete(target) }
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns emptyList()

        useCase.deleteSingleMessage(CHAT_ID, target)

        coVerify(exactly = 1) { messageRepository.delete(target) }
        coVerify(exactly = 0) { chatRepository.upsert(any()) }
    }

    @Test
    fun `deleting a message inside the already-summarized range resets the summary`() = runTest {
        val target = message(5L)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summarizedThrough = 10L, summary = "stale summary")
        coJustRun { messageRepository.delete(target) }
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns emptyList()
        coJustRun { chatRepository.upsert(any()) }

        useCase.deleteSingleMessage(CHAT_ID, target)

        coVerify(exactly = 1) {
            chatRepository.upsert(withArg { updated ->
                assertEquals("", updated.summary)
                assertEquals(0L, updated.summarizedThroughCreatedAt)
            })
        }
    }

    @Test
    fun `rewindTo deletes the target message and every later message`() = runTest {
        val messages = listOf(message(30L), message(40L), message(50L))
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summarizedThrough = 10L)
        coEvery { messageRepository.getFrom(CHAT_ID, 30L) } returns messages
        messages.forEach { coJustRun { messageRepository.delete(it) } }
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns emptyList()

        useCase.rewindTo(CHAT_ID, message(30L))

        messages.forEach { coVerify(exactly = 1) { messageRepository.delete(it) } }
    }

    @Test
    fun `purges only semantic-memory batches that reach into the deleted range`() = runTest {
        val target = message(50L)
        val untouchedFragment = batchFragment(lastCreatedAt = 40L)
        val overlappingFragment = batchFragment(lastCreatedAt = 60L)
        val loreSourcedFragment = MemoryFragmentEntity(
            id = "lore-fragment",
            chatId = CHAT_ID,
            sourceType = MemoryFragmentSource.LORE_ENTRY,
            sourceKey = "some-lore-entry-id",
            text = "lore text",
            embedding = emptyList(),
            createdAt = 999L
        )
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summarizedThrough = 0L)
        coJustRun { messageRepository.delete(target) }
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns
            listOf(untouchedFragment, overlappingFragment, loreSourcedFragment)
        coJustRun { memoryFragmentRepository.delete(any()) }

        useCase.deleteSingleMessage(CHAT_ID, target)

        coVerify(exactly = 1) { memoryFragmentRepository.delete(overlappingFragment) }
        coVerify(exactly = 0) { memoryFragmentRepository.delete(untouchedFragment) }
        coVerify(exactly = 0) { memoryFragmentRepository.delete(loreSourcedFragment) }
    }

    @Test
    fun `purges message-chunk fragments that reach into the deleted range`() = runTest {
        val target = message(50L)
        val untouchedChunk = chunkFragment(lastCreatedAt = 40L)
        val overlappingChunk = chunkFragment(lastCreatedAt = 60L)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summarizedThrough = 0L)
        coJustRun { messageRepository.delete(target) }
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns listOf(untouchedChunk, overlappingChunk)
        coJustRun { memoryFragmentRepository.delete(any()) }

        useCase.deleteSingleMessage(CHAT_ID, target)

        coVerify(exactly = 1) { memoryFragmentRepository.delete(overlappingChunk) }
        coVerify(exactly = 0) { memoryFragmentRepository.delete(untouchedChunk) }
    }

    @Test
    fun `resets lastChunkIndexedAt when the rewind touches before the chunk watermark`() = runTest {
        val target = message(50L)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(lastChunkIndexedAt = 60L)
        coJustRun { messageRepository.delete(target) }
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns emptyList()
        coJustRun { chatRepository.upsert(any()) }

        useCase.deleteSingleMessage(CHAT_ID, target)

        coVerify(exactly = 1) {
            chatRepository.upsert(withArg { updated ->
                assertEquals(0L, updated.lastChunkIndexedAt)
            })
        }
    }

    @Test
    fun `does not reset lastChunkIndexedAt when the rewind stays after the chunk watermark`() = runTest {
        val target = message(80L)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(lastChunkIndexedAt = 60L)
        coJustRun { messageRepository.delete(target) }
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns emptyList()

        useCase.deleteSingleMessage(CHAT_ID, target)

        coVerify(exactly = 0) { chatRepository.upsert(any()) }
    }

    @Test
    fun `deleting nothing is a no-op`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns chat()
        coEvery { messageRepository.getFrom(CHAT_ID, 100L) } returns emptyList()

        useCase.rewindTo(CHAT_ID, message(100L))

        coVerify(exactly = 0) { chatRepository.upsert(any()) }
        coVerify(exactly = 0) { memoryFragmentRepository.getByChat(any()) }
    }

    @Test
    fun `deletes only the chapters reaching into the deleted range and rewinds to the last survivor`() = runTest {
        // Before chapters existed the whole summary had to be wiped, so rewinding a few messages
        // of a long story destroyed all of it.
        val target = message(500L)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summarizedThrough = 800L, summary = "current chapter")
        coJustRun { messageRepository.delete(target) }
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns emptyList()
        coEvery { chatRepository.upsert(any()) } returns Unit
        coEvery { storyChapterRepository.getByChat(CHAT_ID) } returns listOf(
            StoryChapterEntity("c1", CHAT_ID, 1, "Chapitre 1", "…", 0L, 200L, 0L)
        )

        useCase.deleteSingleMessage(CHAT_ID, target)

        coVerify(exactly = 1) { storyChapterRepository.deleteFrom(CHAT_ID, 500L) }
        coVerify(exactly = 1) {
            chatRepository.upsert(withArg { updated ->
                assertEquals("", updated.summary)
                // Resumes from the end of the surviving chapter, not from zero.
                assertEquals(200L, updated.summarizedThroughCreatedAt)
                assertEquals(200L, updated.derivedThroughCreatedAt)
            })
        }
    }

    @Test
    fun `rewinding past every chapter falls back to the start of the story`() = runTest {
        val target = message(10L)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summarizedThrough = 800L, summary = "current chapter")
        coJustRun { messageRepository.delete(target) }
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns emptyList()
        coEvery { chatRepository.upsert(any()) } returns Unit
        coEvery { storyChapterRepository.getByChat(CHAT_ID) } returns emptyList()

        useCase.deleteSingleMessage(CHAT_ID, target)

        coVerify(exactly = 1) {
            chatRepository.upsert(withArg { updated ->
                assertEquals(0L, updated.summarizedThroughCreatedAt)
            })
        }
    }

    @Test
    fun `applies both resets in a single write`() = runTest {
        // BUG-102: the summary reset and the watermark reset used to be two upserts off the same
        // stale snapshot, so when both applied the second silently restored the cleared summary.
        val target = message(50L)
        coEvery { chatRepository.getById(CHAT_ID) } returns
            chat(summarizedThrough = 80L, summary = "a long story so far", lastChunkIndexedAt = 70L)
        coJustRun { messageRepository.delete(target) }
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns emptyList()
        coEvery { chatRepository.upsert(any()) } returns Unit

        useCase.deleteSingleMessage(CHAT_ID, target)

        coVerify(exactly = 1) {
            chatRepository.upsert(withArg { updated ->
                assertEquals("", updated.summary)
                assertEquals(0L, updated.summarizedThroughCreatedAt)
                assertEquals(0L, updated.lastChunkIndexedAt)
            })
        }
    }
}
