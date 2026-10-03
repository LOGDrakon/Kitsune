package com.kitsune.core.memory.timeline

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import com.kitsune.core.data.local.entities.KeyMomentEntity
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.MomentType
import com.kitsune.core.data.local.entities.StoryMood
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.KeyMomentRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatCompletionResult
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val CHAT_ID = "chat-1"

class ExtractKeyMomentsUseCaseTest {

    private val keyMomentRepository = mockk<KeyMomentRepository>()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val chatRepository = mockk<ChatRepository>()
    private val chatCompletionRepository = mockk<ChatCompletionRepository>()
    private val llmModelResolver = mockk<LlmModelResolver> {
        coEvery { resolve(any()) } returns "gpt-4.1"
    }

    private val useCase = ExtractKeyMomentsUseCase(
        keyMomentRepository = keyMomentRepository,
        messageRepository = messageRepository,
        chatRepository = chatRepository,
        chatCompletionRepository = chatCompletionRepository,
        llmModelResolver = llmModelResolver
    )

    private fun chat(storyTimeAnchor: String = "") = ChatEntity(
        id = CHAT_ID,
        universeId = null,
        personaId = "persona-1",
        title = "",
        mode = ChatMode.CHAT,
        storyTimeAnchor = storyTimeAnchor,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun message(index: Int, role: MessageRole) = MessageEntity(
        id = "msg-$index",
        chatId = CHAT_ID,
        role = role,
        content = "message $index",
        imageAttachmentPath = null,
        tokenCount = null,
        createdAt = index.toLong()
    )

    private val batch = listOf(
        message(1, MessageRole.USER),
        message(2, MessageRole.ASSISTANT),
        message(3, MessageRole.USER)
    )

    private fun completion(content: String) =
        Result.success(ChatCompletionResult(content = content, usage = null, modelUsed = "gpt-4.1"))

    private val validResponse = """
        {"moments": [{"type": "CONFESSION", "mood": "TENDER", "title": "L'aveu", "summary": "Aria avoue ses sentiments."}]}
    """.trimIndent()

    private fun stubExtraction(response: String, storyTimeAnchor: String = "") {
        coEvery { keyMomentRepository.getByChat(CHAT_ID) } returns emptyList()
        coEvery { keyMomentRepository.getMaxOrder(CHAT_ID) } returns 0
        coEvery { chatRepository.getById(CHAT_ID) } returns chat(storyTimeAnchor)
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns completion(response)
        coJustRun { keyMomentRepository.upsert(any()) }
    }

    @Test
    fun `does nothing for an empty batch`() = runTest {
        useCase(CHAT_ID, emptyList())

        coVerify(exactly = 0) { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `stamps the chronology anchor, story-time label and message id`() = runTest {
        // BUG-105: messageId used to be hardcoded null, which silently collapsed the novel export's
        // chapter splitting to a single "Chapitre 1".
        stubExtraction(validResponse, storyTimeAnchor = "Early winter, mid-morning")
        val saved = slot<KeyMomentEntity>()
        coJustRun { keyMomentRepository.upsert(capture(saved)) }

        useCase(CHAT_ID, batch)

        assertEquals(batch.last().createdAt, saved.captured.anchorCreatedAt)
        assertEquals("Early winter, mid-morning", saved.captured.storyTimeLabel)
        // Anchored on the last assistant message of the batch — the scene's own text.
        assertEquals("msg-2", saved.captured.messageId)
    }

    @Test
    fun `falls back to the last message when the batch has no assistant turn`() = runTest {
        stubExtraction(validResponse)
        val saved = slot<KeyMomentEntity>()
        coJustRun { keyMomentRepository.upsert(capture(saved)) }
        val userOnly = listOf(message(7, MessageRole.USER), message(8, MessageRole.USER))

        useCase(CHAT_ID, userOnly)

        assertEquals("msg-8", saved.captured.messageId)
    }

    @Test
    fun `parses the moment type and mood`() = runTest {
        stubExtraction(validResponse)
        val saved = slot<KeyMomentEntity>()
        coJustRun { keyMomentRepository.upsert(capture(saved)) }

        useCase(CHAT_ID, batch)

        assertEquals(MomentType.CONFESSION, saved.captured.momentType)
        assertEquals(StoryMood.TENDER, saved.captured.mood)
        assertEquals("L'aveu", saved.captured.title)
    }

    @Test
    fun `tolerates a markdown-fenced response`() = runTest {
        stubExtraction("```json\n$validResponse\n```")

        useCase(CHAT_ID, batch)

        coVerify(exactly = 1) { keyMomentRepository.upsert(any()) }
    }

    @Test
    fun `skips a moment already captured under the same summary`() = runTest {
        stubExtraction(validResponse)
        coEvery { keyMomentRepository.getByChat(CHAT_ID) } returns listOf(
            KeyMomentEntity(
                id = "existing",
                chatId = CHAT_ID,
                messageId = null,
                momentType = MomentType.CONFESSION,
                mood = StoryMood.TENDER,
                title = "L'aveu",
                summary = "Aria avoue ses sentiments.",
                snippets = "",
                isAutoDetected = true,
                createdAt = 0L,
                momentOrder = 1
            )
        )

        useCase(CHAT_ID, batch)

        coVerify(exactly = 0) { keyMomentRepository.upsert(any()) }
    }

    @Test
    fun `writes nothing and does not throw on a malformed response`() = runTest {
        stubExtraction("I could not find any significant moments in this scene.")

        useCase(CHAT_ID, batch)

        coVerify(exactly = 0) { keyMomentRepository.upsert(any()) }
    }

    @Test
    fun `writes nothing when the empty-result shape comes back`() = runTest {
        stubExtraction("""{"moments": []}""")

        useCase(CHAT_ID, batch)

        coVerify(exactly = 0) { keyMomentRepository.upsert(any()) }
    }
}
