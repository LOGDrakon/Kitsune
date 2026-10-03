package com.kitsune.core.network.repository

import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.NetworkPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateImageUseCaseTest {

    private val imageRepository = mockk<ImageGenerationRepository>()
    private val llmModelResolver = mockk<LlmModelResolver> {
        coEvery { resolve(any()) } returns "or::google/gemini-2.5-flash-image"
    }
    private val networkPreferences = mockk<NetworkPreferences> {
        every { getDefaultImageFallbackModelId() } returns null
    }

    private val useCase = GenerateImageUseCase(imageRepository, llmModelResolver, networkPreferences)

    @Test
    fun `fails fast on a blank description without calling the API`() = runTest {
        val result = useCase(characterContext = "Aria", description = "")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { imageRepository.generate(any(), any(), any(), any()) }
    }

    @Test
    fun `returns the images on success`() = runTest {
        val imageBytes = listOf("image-bytes".toByteArray())
        coEvery { imageRepository.generate(any(), any(), any(), any()) } returns Result.success(imageBytes)

        val result = useCase(characterContext = "Aria", description = "a portrait smiling")

        assertEquals(imageBytes, result.getOrThrow())
    }

    @Test
    fun `fails with ImageGenerationRefusedException when the model returns no images at all`() = runTest {
        coEvery { imageRepository.generate(any(), any(), any(), any()) } returns Result.success(emptyList())

        val result = useCase(characterContext = "Aria", description = "a portrait smiling")

        assertTrue(result.exceptionOrNull() is ImageGenerationRefusedException)
    }

    @Test
    fun `passes the reference image when provided, and none otherwise`() = runTest {
        val refs = mutableListOf<List<ByteArray>>()
        coEvery { imageRepository.generate(any(), any(), capture(refs), any()) } returns Result.success(listOf(byteArrayOf(1)))

        val reference = "avatar-bytes".toByteArray()
        useCase(characterContext = "Aria", description = "a portrait", referenceImage = reference)
        useCase(characterContext = "Aria", description = "a portrait")

        assertEquals(listOf(reference), refs[0])
        assertTrue(refs[1].isEmpty())
    }

    @Test
    fun `appends a -ar directive only when an aspect ratio is given`() = runTest {
        val prompts = mutableListOf<String>()
        coEvery { imageRepository.generate(any(), capture(prompts), any(), any()) } returns Result.success(listOf(byteArrayOf(1)))

        useCase(characterContext = "Aria", description = "a portrait", aspectRatio = "16:9")
        useCase(characterContext = "Aria", description = "a portrait")

        assertTrue(prompts[0].contains("-ar 16:9"))
        assertTrue(!prompts[1].contains("-ar"))
    }

    @Test
    fun `retries once with the fallback model when one is set and the first model fails`() = runTest {
        every { networkPreferences.getDefaultImageFallbackModelId() } returns "or::other/image-model"
        val models = slot<String>()
        coEvery { imageRepository.generate("or::google/gemini-2.5-flash-image", any(), any(), any()) } returns
            Result.failure(IllegalStateException("down"))
        coEvery { imageRepository.generate(capture(models), any(), any(), any()) } answers {
            if (models.captured == "or::other/image-model") Result.success(listOf(byteArrayOf(2)))
            else Result.failure(IllegalStateException("down"))
        }

        val result = useCase(characterContext = "Aria", description = "a portrait")

        assertTrue(result.isSuccess)
        assertEquals("or::other/image-model", models.captured)
    }
}
