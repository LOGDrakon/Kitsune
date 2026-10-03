package com.kitsune.core.network.visualsheet

import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatCompletionResult
import com.kitsune.core.security.locale.AppLanguage
import com.kitsune.core.security.locale.AppLanguageManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateVisualSheetUseCaseTest {

    private val chatCompletionRepository = mockk<ChatCompletionRepository>()
    private val llmModelResolver = mockk<LlmModelResolver> {
        coEvery { resolve(any()) } returns "gpt-4.1"
    }
    private val appLanguageManager = mockk<AppLanguageManager> {
        every { getSelectedLanguage() } returns AppLanguage.ENGLISH
    }

    private val useCase = GenerateVisualSheetUseCase(chatCompletionRepository, llmModelResolver, appLanguageManager)

    private fun completionResult(content: String) =
        Result.success(ChatCompletionResult(content = content, usage = null, modelUsed = "gpt-4.1"))

    @Test
    fun `parses all four fields from the AI json response`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns completionResult(
            """
            {
              "physicalTraits": "tall, athletic build, long silver hair, emerald green eyes",
              "artStyle": "semi-realistic digital painting",
              "colorPalette": "emerald green, silver, navy blue",
              "defaultOutfit": "black leather jacket over a navy turtleneck"
            }
            """.trimIndent()
        )

        val sheet = useCase("Aria", "A mysterious mercenary", "Cold but fiercely loyal").getOrThrow()

        assertEquals("tall, athletic build, long silver hair, emerald green eyes", sheet.physicalTraits)
        assertEquals("semi-realistic digital painting", sheet.artStyle)
        assertEquals("emerald green, silver, navy blue", sheet.colorPalette)
        assertEquals("black leather jacket over a navy turtleneck", sheet.defaultOutfit)
    }

    @Test
    fun `uses the LLM model resolver`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns completionResult(
            """{"physicalTraits":"a","artStyle":"b","colorPalette":"c","defaultOutfit":"d"}"""
        )

        useCase("Aria", "A mysterious mercenary", "")

        io.mockk.coVerify { llmModelResolver.resolve(any()) }
    }

    @Test
    fun `propagates failure when the completion call fails`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("network down"))

        val result = useCase("Aria", "A mysterious mercenary", "")

        assertTrue(result.isFailure)
    }
}
