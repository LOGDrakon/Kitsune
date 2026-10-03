package com.kitsune.core.network.repository

import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.preferences.LlmModelResolver
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DescribeSceneForImageUseCaseTest {

    private val chatCompletionRepository = mockk<ChatCompletionRepository>()
    private val llmModelResolver = mockk<LlmModelResolver> {
        coEvery { resolve(any()) } returns "deepseek-v4-flash"
    }

    private val useCase = DescribeSceneForImageUseCase(chatCompletionRepository, llmModelResolver)

    private fun turn(content: String) = ChatTurn(role = ChatMessageDto.ROLE_USER, content = content)

    @Test
    fun `fails fast when there is no conversation yet`() = runTest {
        val result = useCase(recentTurns = emptyList())

        assertTrue(result.isFailure)
    }

    @Test
    fun `returns the trimmed scene description on success`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "  She leans against the window, moonlight on her face.  ", usage = null, modelUsed = "x")
        )

        val result = useCase(recentTurns = listOf(turn("j'arrive")))

        assertEquals("She leans against the window, moonlight on her face.", result.getOrThrow())
    }

    @Test
    fun `fails when the model returns a blank description`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "   ", usage = null, modelUsed = "x")
        )

        val result = useCase(recentTurns = listOf(turn("j'arrive")))

        assertTrue(result.isFailure)
    }

    @Test
    fun `uses the chat model, not the image model`() = runTest {
        val modelSlot = slot<String>()
        coEvery { chatCompletionRepository.complete(capture(modelSlot), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "A quiet room.", usage = null, modelUsed = "x")
        )

        useCase(recentTurns = listOf(turn("j'arrive")))

        assertEquals("deepseek-v4-flash", modelSlot.captured)
    }

    @Test
    fun `passes the conversation turns through unchanged`() = runTest {
        val messagesSlot = slot<List<ChatTurn>>()
        coEvery { chatCompletionRepository.complete(any(), any(), capture(messagesSlot), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "A quiet room.", usage = null, modelUsed = "x")
        )
        val turns = listOf(turn("j'arrive"), turn("*elle sourit*"))

        useCase(recentTurns = turns)

        assertEquals(turns, messagesSlot.captured)
    }

    @Test
    fun `uses the mature system prompt only when allowed`() = runTest {
        val promptSlot = slot<String>()
        coEvery { chatCompletionRepository.complete(any(), capture(promptSlot), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "A quiet room.", usage = null, modelUsed = "x")
        )

        useCase(recentTurns = listOf(turn("j'arrive")), allowMatureContent = true)

        assertTrue(promptSlot.captured.contains("CONTENT POLICY"))
    }

    @Test
    fun `folds the character context into the system prompt when given`() = runTest {
        val promptSlot = slot<String>()
        coEvery { chatCompletionRepository.complete(any(), capture(promptSlot), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "A quiet room.", usage = null, modelUsed = "x")
        )

        useCase(recentTurns = listOf(turn("j'arrive")), characterContext = "Aria: red hair, leather jacket.")

        assertTrue(promptSlot.captured.contains("Aria: red hair, leather jacket."))
    }

    @Test
    fun `omits the character sheet section when no context is given`() = runTest {
        val promptSlot = slot<String>()
        coEvery { chatCompletionRepository.complete(any(), capture(promptSlot), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "A quiet room.", usage = null, modelUsed = "x")
        )

        useCase(recentTurns = listOf(turn("j'arrive")))

        assertTrue(!promptSlot.captured.contains("Character sheet:"))
    }

    @Test
    fun `strips quoted dialogue the model included despite instructions`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(
                content = "She leans in and whispers \"I missed you\" before smiling softly.",
                usage = null,
                modelUsed = "x"
            )
        )

        val result = useCase(recentTurns = listOf(turn("j'arrive")))

        assertEquals("She leans in and whispers before smiling softly.", result.getOrThrow())
    }

    @Test
    fun `strips dialogue in French guillemets too`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "He says «reste avec moi» and reaches for her hand.", usage = null, modelUsed = "x")
        )

        val result = useCase(recentTurns = listOf(turn("j'arrive")))

        assertEquals("He says and reaches for her hand.", result.getOrThrow())
    }
}
