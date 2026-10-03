package com.kitsune.core.network.repository

import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.NetworkPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateImageUseCaseTest {

    private val chatCompletionRepository = mockk<ChatCompletionRepository>()
    private val llmModelResolver = mockk<LlmModelResolver> {
        coEvery { resolve(any()) } returns "gemini-3.1-flash-image-preview"
    }
    private val networkPreferences = mockk<NetworkPreferences> {
        every { getDefaultImageModelId() } returns "gemini-3.1-flash-image-preview"
        every { getDefaultImageFallbackModelId() } returns "gemini-3.1-flash-image-preview"
    }

    private val useCase = GenerateImageUseCase(chatCompletionRepository, llmModelResolver, networkPreferences)

    @Test
    fun `fails fast on a blank description without calling the API`() = runTest {
        val result = useCase(characterContext = "Aria", description = "")

        assertTrue(result.isFailure)
    }

    @Test
    fun `returns the decoded images on success`() = runTest {
        val imageBytes = listOf("image-bytes".toByteArray())
        coEvery {
            chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ChatCompletionResult(content = "", usage = null, modelUsed = "gemini-3.1-flash-image-preview", images = imageBytes))

        val result = useCase(characterContext = "Aria", description = "a portrait smiling")

        assertEquals(imageBytes, result.getOrThrow())
    }

    @Test
    fun `fails with ImageGenerationRefusedException when the model returns no images at all`() = runTest {
        coEvery {
            chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ChatCompletionResult(content = "sorry, no image", usage = null, modelUsed = "gemini-3.1-flash-image-preview"))

        val result = useCase(characterContext = "Aria", description = "a portrait smiling")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ImageGenerationRefusedException)
    }

    @Test
    fun `attaches the reference image on the outgoing turn when provided`() = runTest {
        val messagesSlot = slot<List<ChatTurn>>()
        coEvery {
            chatCompletionRepository.complete(any(), any(), capture(messagesSlot), any(), any(), any(), any(), any(), any())
        } returns Result.success(ChatCompletionResult(content = "", usage = null, modelUsed = "x", images = listOf(byteArrayOf(1))))

        val reference = "avatar-bytes".toByteArray()
        useCase(characterContext = "Aria", description = "a portrait", referenceImage = reference)

        assertEquals(listOf(reference), messagesSlot.captured.single().referenceImages)
    }

    @Test
    fun `sends no reference images when none is provided`() = runTest {
        val messagesSlot = slot<List<ChatTurn>>()
        coEvery {
            chatCompletionRepository.complete(any(), any(), capture(messagesSlot), any(), any(), any(), any(), any(), any())
        } returns Result.success(ChatCompletionResult(content = "", usage = null, modelUsed = "x", images = listOf(byteArrayOf(1))))

        useCase(characterContext = "Aria", description = "a portrait")

        assertTrue(messagesSlot.captured.single().referenceImages.isEmpty())
    }

    @Test
    fun `appends a -ar directive to the prompt when an aspect ratio is given`() = runTest {
        val messagesSlot = slot<List<ChatTurn>>()
        coEvery {
            chatCompletionRepository.complete(any(), any(), capture(messagesSlot), any(), any(), any(), any(), any(), any())
        } returns Result.success(ChatCompletionResult(content = "", usage = null, modelUsed = "x", images = listOf(byteArrayOf(1))))

        useCase(characterContext = "Aria", description = "a portrait", aspectRatio = "16:9")

        assertTrue(messagesSlot.captured.single().content.contains("-ar 16:9"))
    }

    @Test
    fun `omits the -ar directive when no aspect ratio is given`() = runTest {
        val messagesSlot = slot<List<ChatTurn>>()
        coEvery {
            chatCompletionRepository.complete(any(), any(), capture(messagesSlot), any(), any(), any(), any(), any(), any())
        } returns Result.success(ChatCompletionResult(content = "", usage = null, modelUsed = "x", images = listOf(byteArrayOf(1))))

        useCase(characterContext = "Aria", description = "a portrait")

        assertTrue(!messagesSlot.captured.single().content.contains("-ar"))
    }
}
