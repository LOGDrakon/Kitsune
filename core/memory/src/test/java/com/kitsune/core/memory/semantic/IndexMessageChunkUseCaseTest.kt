package com.kitsune.core.memory.semantic

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.memory.summarization.SummarizationConfig
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHAT_ID = "chat-1"

class IndexMessageChunkUseCaseTest {

    private val chatRepository = mockk<ChatRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val indexMemoryFragmentUseCase = mockk<IndexMemoryFragmentUseCase>()

    private val useCase = IndexMessageChunkUseCase(chatRepository, messageRepository, indexMemoryFragmentUseCase)

    private fun chat(lastChunkIndexedAt: Long = 0L) = ChatEntity(
        id = CHAT_ID,
        universeId = null,
        personaId = "persona-1",
        title = "",
        mode = ChatMode.CHAT,
        lastChunkIndexedAt = lastChunkIndexedAt,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun message(createdAt: Long, index: Int) = MessageEntity(
        id = "msg-$createdAt",
        chatId = CHAT_ID,
        role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
        content = "message $createdAt",
        imageAttachmentPath = null,
        tokenCount = null,
        createdAt = createdAt
    )

    /** Ascending, strictly after [since] — what `getAfter` returns. */
    private fun messages(count: Int, since: Long = 0L) =
        (1..count).mapIndexed { i, n -> message(since + n, i) }

    private fun stubIndexing() {
        coJustRun { indexMemoryFragmentUseCase(any(), any(), any(), any()) }
        coEvery { chatRepository.upsert(any()) } returns Unit
    }

    @Test
    fun `does nothing when fewer than CHUNK_INTERVAL messages are available`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns chat()
        coEvery { messageRepository.getAfter(CHAT_ID, 0L) } returns messages(SummarizationConfig.CHUNK_INTERVAL - 1)

        useCase(CHAT_ID)

        coVerify(exactly = 0) { indexMemoryFragmentUseCase(any(), any(), any(), any()) }
        coVerify(exactly = 0) { chatRepository.upsert(any()) }
    }

    @Test
    fun `indexes a chunk when exactly CHUNK_INTERVAL messages are available`() = runTest {
        val pending = messages(SummarizationConfig.CHUNK_INTERVAL)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat()
        coEvery { messageRepository.getAfter(CHAT_ID, 0L) } returns pending
        stubIndexing()

        useCase(CHAT_ID)

        coVerify(exactly = 1) {
            indexMemoryFragmentUseCase(CHAT_ID, MemoryFragmentSource.MESSAGE_CHUNK, "chunk-${pending.last().createdAt}", any())
        }
    }

    @Test
    fun `indexes the chunk text oldest-first`() = runTest {
        // BUG-103: the first chunk of a chat was read via getRecent (DESC), so the indexed text ran
        // backwards — the embedding described the scene in reverse.
        val pending = messages(SummarizationConfig.CHUNK_INTERVAL)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat()
        coEvery { messageRepository.getAfter(CHAT_ID, 0L) } returns pending
        val text = slot<String>()
        coJustRun { indexMemoryFragmentUseCase(any(), any(), any(), capture(text)) }
        coEvery { chatRepository.upsert(any()) } returns Unit

        useCase(CHAT_ID)

        val lines = text.captured.lines()
        assertTrue("first line should be the oldest message", lines.first().endsWith(pending.first().content))
        assertTrue("last line should be the newest message", lines.last().endsWith(pending.last().content))
    }

    @Test
    fun `sets the watermark to the newest message of the chunk`() = runTest {
        // BUG-103: with the DESC read, chunk.last() was the OLDEST of the eight, so the watermark
        // moved backwards and the same messages were re-indexed forever.
        val pending = messages(SummarizationConfig.CHUNK_INTERVAL)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat()
        coEvery { messageRepository.getAfter(CHAT_ID, 0L) } returns pending
        stubIndexing()

        useCase(CHAT_ID)

        coVerify(exactly = 1) {
            chatRepository.upsert(withArg { updated ->
                assertEquals(pending.maxOf { it.createdAt }, updated.lastChunkIndexedAt)
            })
        }
    }

    @Test
    fun `reads strictly after the watermark so the boundary message is not re-indexed`() = runTest {
        // BUG-103: getFrom uses `>=`, so the watermark message itself was pulled into every
        // subsequent chunk.
        val since = 100L
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(lastChunkIndexedAt = since)
        coEvery { messageRepository.getAfter(CHAT_ID, since) } returns messages(SummarizationConfig.CHUNK_INTERVAL, since)
        stubIndexing()

        useCase(CHAT_ID)

        coVerify(exactly = 1) { messageRepository.getAfter(CHAT_ID, since) }
        coVerify(exactly = 0) { messageRepository.getFrom(any(), any()) }
        coVerify(exactly = 0) { messageRepository.getRecent(any(), any()) }
    }

    @Test
    fun `drains a backlog into several chunks in one run with a single watermark write`() = runTest {
        val pending = messages(SummarizationConfig.CHUNK_INTERVAL * 3)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat()
        coEvery { messageRepository.getAfter(CHAT_ID, 0L) } returns pending
        stubIndexing()

        useCase(CHAT_ID)

        coVerify(exactly = 3) { indexMemoryFragmentUseCase(CHAT_ID, MemoryFragmentSource.MESSAGE_CHUNK, any(), any()) }
        coVerify(exactly = 1) {
            chatRepository.upsert(withArg { updated ->
                assertEquals(pending.last().createdAt, updated.lastChunkIndexedAt)
            })
        }
    }

    @Test
    fun `bounds how many chunks a single run indexes`() = runTest {
        // A freshly imported or restored chat must not fire an unbounded burst of embedding calls.
        coEvery { chatRepository.getById(CHAT_ID) } returns chat()
        coEvery { messageRepository.getAfter(CHAT_ID, 0L) } returns messages(SummarizationConfig.CHUNK_INTERVAL * 20)
        stubIndexing()

        useCase(CHAT_ID)

        coVerify(atMost = 5) { indexMemoryFragmentUseCase(CHAT_ID, MemoryFragmentSource.MESSAGE_CHUNK, any(), any()) }
    }
}
