package com.kitsune.core.network.repository

import com.kitsune.core.network.preferences.LlmModelResolver
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftenImagePromptUseCaseTest {

    private val chatCompletionRepository = mockk<ChatCompletionRepository>()
    private val llmModelResolver = mockk<LlmModelResolver> {
        coEvery { resolve(any()) } returns "deepseek-v4-flash"
    }

    private val useCase = SoftenImagePromptUseCase(chatCompletionRepository, llmModelResolver)

    @Test
    fun `fails fast on a blank original description without calling the API`() = runTest {
        val result = useCase(originalDescription = "", characterContext = "Aria")

        assertTrue(result.isFailure)
    }

    @Test
    fun `returns the trimmed softened prompt on success`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "  She lies back, her red dress loosened at the shoulder.  ", usage = null, modelUsed = "x")
        )

        val result = useCase(originalDescription = "explicit scene", characterContext = "Aria: wearing a red dress")

        assertEquals("She lies back, her red dress loosened at the shoulder.", result.getOrThrow())
    }

    @Test
    fun `fails when the model returns a blank rewrite`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "   ", usage = null, modelUsed = "x")
        )

        val result = useCase(originalDescription = "explicit scene", characterContext = "")

        assertTrue(result.isFailure)
    }

    @Test
    fun `uses the chat model, not the image model`() = runTest {
        val modelSlot = slot<String>()
        coEvery { chatCompletionRepository.complete(capture(modelSlot), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "A softened prompt.", usage = null, modelUsed = "x")
        )

        useCase(originalDescription = "explicit scene", characterContext = "")

        assertEquals("deepseek-v4-flash", modelSlot.captured)
    }

    @Test
    fun `includes the original description and character context in the outgoing message`() = runTest {
        val messagesSlot = slot<List<ChatTurn>>()
        coEvery { chatCompletionRepository.complete(any(), any(), capture(messagesSlot), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "A softened prompt.", usage = null, modelUsed = "x")
        )

        useCase(originalDescription = "explicit scene", characterContext = "Aria: red dress")

        val sentContent = messagesSlot.captured.single().content
        assertTrue(sentContent.contains("explicit scene"))
        assertTrue(sentContent.contains("Aria: red dress"))
    }
}
