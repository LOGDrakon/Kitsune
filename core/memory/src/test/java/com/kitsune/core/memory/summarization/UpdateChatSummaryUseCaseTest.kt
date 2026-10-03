package com.kitsune.core.memory.summarization

import com.kitsune.core.common.memory.MemorySettingsHolder
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MemoryFragmentRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.StoryChapterRepository
import com.kitsune.core.memory.lore.ExtractLoreEntriesUseCase
import com.kitsune.core.memory.lore.SyncCastFromLoreUseCase
import com.kitsune.core.memory.semantic.EmbeddingCache
import com.kitsune.core.memory.semantic.IndexMemoryFragmentUseCase
import com.kitsune.core.memory.timeline.ExtractKeyMomentsUseCase
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatCompletionResult
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val CHAT_ID = "chat-1"

class UpdateChatSummaryUseCaseTest {

    private val chatRepository = mockk<ChatRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val chatCompletionRepository = mockk<ChatCompletionRepository>()
    private val llmModelResolver = mockk<LlmModelResolver> {
        coEvery { resolve(any()) } returns "gpt-4.1"
    }
    private val extractLoreEntriesUseCase = mockk<ExtractLoreEntriesUseCase>()
    private val indexMemoryFragmentUseCase = mockk<IndexMemoryFragmentUseCase>()
    private val syncCastFromLoreUseCase = mockk<SyncCastFromLoreUseCase>()
    private val extractKeyMomentsUseCase = mockk<ExtractKeyMomentsUseCase>(relaxed = true)
    private val memoryFragmentRepository = mockk<MemoryFragmentRepository>()
    private val storyChapterRepository = mockk<StoryChapterRepository>(relaxed = true)
    private val embeddingCache = EmbeddingCache()

    private val useCase = UpdateChatSummaryUseCase(
        chatRepository = chatRepository,
        messageRepository = messageRepository,
        chatCompletionRepository = chatCompletionRepository,
        llmModelResolver = llmModelResolver,
        extractLoreEntriesUseCase = extractLoreEntriesUseCase,
        syncCastFromLoreUseCase = syncCastFromLoreUseCase,
        indexMemoryFragmentUseCase = indexMemoryFragmentUseCase,
        extractKeyMomentsUseCase = extractKeyMomentsUseCase,
        memoryFragmentRepository = memoryFragmentRepository,
        storyChapterRepository = storyChapterRepository,
        embeddingCache = embeddingCache
    )

    /** [MemorySettingsHolder] is a mutable global pushed from the backend at startup; reset it so a
     *  test that overrides the window can't leak into the next one. */
    @Before
    fun resetMemorySettings() {
        MemorySettingsHolder.apply(
            maxContextTokens = null,
            rawWindowSize = 40,
            rawWindowSizePro = 60,
            loreEntries = 12,
            loreEntriesPro = 20,
            samplingEnabled = true
        )
    }

    private fun window(isPro: Boolean = false) = SummarizationConfig.reservedWindow(isPro)

    /** Smallest backlog that triggers a fold, for a chat that already has a summary. */
    private fun triggeringBacklogSize(isPro: Boolean = false) =
        window(isPro) + SummarizationConfig.SUMMARIZE_BATCH_MIN

    private fun chat(summarizedThrough: Long = 0L, summary: String = "") = ChatEntity(
        id = CHAT_ID,
        universeId = null,
        personaId = "persona-1",
        title = "",
        mode = ChatMode.CHAT,
        summary = summary,
        summarizedThroughCreatedAt = summarizedThrough,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun message(index: Int) = MessageEntity(
        id = "msg-$index",
        chatId = CHAT_ID,
        role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
        content = "message $index",
        imageAttachmentPath = null,
        tokenCount = null,
        createdAt = index.toLong()
    )

    private fun completion(content: String) =
        Result.success(ChatCompletionResult(content = content, usage = null, modelUsed = "gpt-5-mini"))

    private fun stubDownstreamUseCases() {
        coJustRun { extractLoreEntriesUseCase(any(), any(), any()) }
        coJustRun { indexMemoryFragmentUseCase(any(), any(), any(), any()) }
        coJustRun { syncCastFromLoreUseCase(any()) }
        coEvery { memoryFragmentRepository.getByChat(any()) } returns emptyList()
    }

    private fun stubHappyPath(backlog: List<MessageEntity>, existingSummary: String = "seeded summary") {
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summary = existingSummary)
        coEvery { messageRepository.getUnsummarized(CHAT_ID, 0L) } returns backlog
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns completion("updated summary")
        coEvery { chatRepository.upsert(any()) } returns Unit
        stubDownstreamUseCases()
    }

    @Test
    fun `does nothing when backlog is below the trigger threshold`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns chat()
        coEvery { messageRepository.getUnsummarized(CHAT_ID, 0L) } returns (1..10).map(::message)

        useCase(CHAT_ID)

        coVerify(exactly = 0) { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { extractLoreEntriesUseCase(any(), any(), any()) }
        coVerify(exactly = 0) { indexMemoryFragmentUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun `does not trigger while the backlog only covers the raw window`() = runTest {
        // Everything unsummarized is still being sent verbatim, so there is nothing to fold yet.
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summary = "seeded")
        coEvery { messageRepository.getUnsummarized(CHAT_ID, 0L) } returns (1..window()).map(::message)

        useCase(CHAT_ID)

        coVerify(exactly = 0) { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the folded batch never overlaps the raw window`() = runTest {
        // BUG-104: the batch used to reserve a hardcoded 30 while the prompt sent rawWindowSize,
        // so the last messages were both summarized and still sent verbatim.
        val backlog = (1..triggeringBacklogSize()).map(::message)
        stubHappyPath(backlog)

        useCase(CHAT_ID)

        val expectedBatch = backlog.dropLast(window())
        assertEquals(SummarizationConfig.SUMMARIZE_BATCH_MIN, expectedBatch.size)
        coVerify(exactly = 1) {
            chatRepository.upsert(
                withArg { updated ->
                    assertEquals("updated summary", updated.summary)
                    assertEquals(expectedBatch.last().createdAt, updated.summarizedThroughCreatedAt)
                }
            )
        }
    }

    @Test
    fun `pro chats still trigger and still produce a non-empty batch`() = runTest {
        // Regression guard for the trap in the BUG-104 fix: naively swapping the constant for
        // rawWindowSize(isPro) while leaving a flat gate yields an empty batch in Pro, i.e. a
        // permanently dead memory pipeline for every Pro chat.
        val backlog = (1..triggeringBacklogSize(isPro = true)).map(::message)
        stubHappyPath(backlog)

        useCase(CHAT_ID, isPro = true)

        val expectedBatch = backlog.dropLast(window(isPro = true))
        assertTrue("Pro batch must not be empty", expectedBatch.isNotEmpty())
        coVerify(exactly = 1) { extractLoreEntriesUseCase(CHAT_ID, expectedBatch, true) }
        coVerify(exactly = 1) { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a chat with no summary yet folds at the lower first-batch threshold`() = runTest {
        val backlog = (1..(window() + SummarizationConfig.FIRST_BATCH_MIN)).map(::message)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summary = "")
        coEvery { messageRepository.getUnsummarized(CHAT_ID, 0L) } returns backlog
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns completion("first summary")
        coEvery { chatRepository.upsert(any()) } returns Unit
        stubDownstreamUseCases()

        useCase(CHAT_ID)

        coVerify(exactly = 1) { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { extractLoreEntriesUseCase(CHAT_ID, backlog.dropLast(window()), false) }
    }

    @Test
    fun `a chat that already has a summary waits for the full batch threshold`() = runTest {
        // Same backlog as the test above, but with a summary already present — the lower
        // first-batch bar must not apply a second time.
        val backlog = (1..(window() + SummarizationConfig.FIRST_BATCH_MIN)).map(::message)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summary = "already summarized")
        coEvery { messageRepository.getUnsummarized(CHAT_ID, 0L) } returns backlog

        useCase(CHAT_ID)

        coVerify(exactly = 0) { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `uses the model returned by the LLM resolver`() = runTest {
        stubHappyPath((1..triggeringBacklogSize()).map(::message))

        useCase(CHAT_ID)

        coVerify { llmModelResolver.resolve(any()) }
        coVerify { chatCompletionRepository.complete("gpt-4.1", any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `freezes a chapter and resets the rolling summary once it grows past the threshold`() = runTest {
        val backlog = (1..triggeringBacklogSize()).map(::message)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summary = "seeded summary")
        coEvery { messageRepository.getUnsummarized(CHAT_ID, 0L) } returns backlog
        val longSummary = "x".repeat(SummarizationConfig.META_SUMMARY_TRIGGER_CHARS + 1)
        coEvery {
            chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returnsMany listOf(
            completion(longSummary),
            completion("""{"title": "Le port sous la pluie", "summary": "Aria scelle un pacte."}""")
        )
        coEvery { chatRepository.upsert(any()) } returns Unit
        coEvery { storyChapterRepository.getMaxChapterIndex(CHAT_ID) } returns 2
        stubDownstreamUseCases()

        useCase(CHAT_ID)

        // Same single extra call the old flat compaction made — repurposed, not added.
        coVerify(exactly = 2) { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            storyChapterRepository.upsert(withArg { chapter ->
                assertEquals(3, chapter.chapterIndex)
                assertEquals("Le port sous la pluie", chapter.title)
                assertEquals("Aria scelle un pacte.", chapter.summary)
                assertEquals(backlog.dropLast(window()).last().createdAt, chapter.throughCreatedAt)
            })
        }
        coVerify {
            chatRepository.upsert(withArg { updated -> assertEquals("", updated.summary) })
        }
    }

    @Test
    fun `keeps the raw summary as the chapter body when the close response is malformed`() = runTest {
        val backlog = (1..triggeringBacklogSize()).map(::message)
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(summary = "seeded summary")
        coEvery { messageRepository.getUnsummarized(CHAT_ID, 0L) } returns backlog
        val longSummary = "x".repeat(SummarizationConfig.META_SUMMARY_TRIGGER_CHARS + 1)
        coEvery {
            chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returnsMany listOf(completion(longSummary), completion("Sorry, I can't do that."))
        coEvery { chatRepository.upsert(any()) } returns Unit
        coEvery { storyChapterRepository.getMaxChapterIndex(CHAT_ID) } returns 0
        stubDownstreamUseCases()

        useCase(CHAT_ID)

        // Degrade, never lose: the chapter still gets written, carrying the uncompacted text.
        coVerify(exactly = 1) {
            storyChapterRepository.upsert(withArg { chapter -> assertEquals(longSummary, chapter.summary) })
        }
    }

    @Test
    fun `does not advance the derived cursor when a derived step failed`() = runTest {
        val backlog = (1..triggeringBacklogSize()).map(::message)
        stubHappyPath(backlog)
        coEvery { extractLoreEntriesUseCase(any(), any(), any()) } throws IllegalStateException("boom")

        useCase(CHAT_ID)

        // The summary still advances; the derived cursor must not, so the batch is replayed.
        coVerify(exactly = 1) {
            chatRepository.upsert(withArg { updated ->
                assertEquals(0L, updated.derivedThroughCreatedAt)
                assertEquals(1, updated.derivedRetryCount)
            })
        }
    }

    @Test
    fun `advances the derived cursor when every derived step succeeded`() = runTest {
        val backlog = (1..triggeringBacklogSize()).map(::message)
        stubHappyPath(backlog)

        useCase(CHAT_ID)

        coVerify(exactly = 1) {
            chatRepository.upsert(withArg { updated ->
                assertEquals(backlog.dropLast(window()).last().createdAt, updated.derivedThroughCreatedAt)
                assertEquals(0, updated.derivedRetryCount)
            })
        }
    }

    @Test
    fun `gives up on a batch whose derived steps keep failing`() = runTest {
        val backlog = (1..triggeringBacklogSize()).map(::message)
        stubHappyPath(backlog)
        // Already at the retry ceiling: replaying four API calls forever is worse than moving on.
        coEvery { chatRepository.getById(CHAT_ID) } returns
            chat(summary = "seeded summary").copy(derivedRetryCount = 2)
        coEvery { extractLoreEntriesUseCase(any(), any(), any()) } throws IllegalStateException("boom")

        useCase(CHAT_ID)

        coVerify(exactly = 1) {
            chatRepository.upsert(withArg { updated ->
                assertEquals(backlog.dropLast(window()).last().createdAt, updated.derivedThroughCreatedAt)
                assertEquals(0, updated.derivedRetryCount)
            })
        }
    }

    @Test
    fun `triggers level 3 lore extraction and level 4 semantic indexing with the same batch`() = runTest {
        val backlog = (1..triggeringBacklogSize()).map(::message)
        stubHappyPath(backlog)

        useCase(CHAT_ID)

        val expectedBatch = backlog.dropLast(window())
        coVerify(exactly = 1) { extractLoreEntriesUseCase(CHAT_ID, expectedBatch, false) }
        coVerify(exactly = 1) {
            indexMemoryFragmentUseCase(CHAT_ID, MemoryFragmentSource.SUMMARY_BATCH, any(), any())
        }
    }

    @Test
    fun `prunes the message chunks the new batch fragment now covers`() = runTest {
        val backlog = (1..triggeringBacklogSize()).map(::message)
        val batch = backlog.dropLast(window())
        val insideBatch = fragment("chunk-inside", MemoryFragmentSource.MESSAGE_CHUNK, "chunk-${batch.last().createdAt}")
        val stillInWindow = fragment("chunk-window", MemoryFragmentSource.MESSAGE_CHUNK, "chunk-${backlog.last().createdAt}")
        val batchFragment = fragment("batch-frag", MemoryFragmentSource.SUMMARY_BATCH, "batch-${batch.last().createdAt}")
        stubHappyPath(backlog)
        coEvery { memoryFragmentRepository.getByChat(CHAT_ID) } returns listOf(insideBatch, stillInWindow, batchFragment)
        coJustRun { memoryFragmentRepository.delete(any()) }

        useCase(CHAT_ID)

        coVerify(exactly = 1) { memoryFragmentRepository.delete(insideBatch) }
        coVerify(exactly = 0) { memoryFragmentRepository.delete(stillInWindow) }
        coVerify(exactly = 0) { memoryFragmentRepository.delete(batchFragment) }
    }

    @Test
    fun `still updates the summary even when lore extraction fails`() = runTest {
        val backlog = (1..triggeringBacklogSize()).map(::message)
        stubHappyPath(backlog)
        coEvery { extractLoreEntriesUseCase(any(), any(), any()) } throws IllegalStateException("boom")

        useCase(CHAT_ID)

        coVerify(exactly = 1) { chatRepository.upsert(withArg { updated -> assertEquals("updated summary", updated.summary) }) }
    }

    private fun fragment(id: String, sourceType: MemoryFragmentSource, sourceKey: String) = MemoryFragmentEntity(
        id = id,
        chatId = CHAT_ID,
        sourceType = sourceType,
        sourceKey = sourceKey,
        text = "text",
        embedding = listOf(1f, 0f),
        createdAt = 0L
    )
}
