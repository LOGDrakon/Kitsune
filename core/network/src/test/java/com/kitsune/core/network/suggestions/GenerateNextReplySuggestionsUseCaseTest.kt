package com.kitsune.core.network.suggestions

import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.repository.ChatCompletionResult
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.security.locale.AppLanguage
import com.kitsune.core.security.locale.AppLanguageManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateNextReplySuggestionsUseCaseTest {

    private val chatCompletionRepository = mockk<ChatCompletionRepository>()
    private val llmModelResolver = mockk<LlmModelResolver> {
        coEvery { resolve(any()) } returns "deepseek-v4-flash"
    }
    private val appLanguageManager = mockk<AppLanguageManager> {
        every { getSelectedLanguage() } returns AppLanguage.ENGLISH
    }

    private val useCase = GenerateNextReplySuggestionsUseCase(chatCompletionRepository, llmModelResolver, appLanguageManager)

    private fun turn(content: String) = ChatTurn(role = "user", content = content)

    @Test
    fun `fails fast on an empty conversation without calling the API`() = runTest {
        val result = useCase(emptyList())

        assertTrue(result.isFailure)
    }

    @Test
    fun `parses three newline-separated suggestions, trimming stray bullets and quotes`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(
                content = "- \"Turn around and face him.\"\n* Say nothing and walk away.\n\"Ask her what she meant.\"",
                usage = null,
                modelUsed = "x"
            )
        )

        val result = useCase(listOf(turn("Some earlier line.")))

        assertEquals(
            listOf("Turn around and face him.", "Say nothing and walk away.", "Ask her what she meant."),
            result.getOrThrow()
        )
    }

    @Test
    fun `fails when the model returns no usable suggestions`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "   \n  \n", usage = null, modelUsed = "x")
        )

        val result = useCase(listOf(turn("Some earlier line.")))

        assertTrue(result.isFailure)
    }

    @Test
    fun `caps at three suggestions even if the model returns more lines`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "One.\nTwo.\nThree.\nFour.\nFive.", usage = null, modelUsed = "x")
        )

        val result = useCase(listOf(turn("Some earlier line.")))

        assertEquals(listOf("One.", "Two.", "Three."), result.getOrThrow())
    }

    @Test
    fun `uses the dedicated next-reply-suggestions model resolution, not the chat model`() = runTest {
        val modelSlot = slot<String>()
        coEvery { chatCompletionRepository.complete(capture(modelSlot), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "One.\nTwo.\nThree.", usage = null, modelUsed = "x")
        )

        useCase(listOf(turn("Some earlier line.")))

        assertEquals("deepseek-v4-flash", modelSlot.captured)
    }

    @Test
    fun `passes the recent conversation through unchanged as the outgoing messages`() = runTest {
        val messagesSlot = slot<List<ChatTurn>>()
        coEvery { chatCompletionRepository.complete(any(), any(), capture(messagesSlot), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = "One.\nTwo.\nThree.", usage = null, modelUsed = "x")
        )
        val history = listOf(turn("Hello there."), turn("How are you?"))

        useCase(history)

        assertEquals(history, messagesSlot.captured)
    }
}
