package com.kitsune.feature.chat.novel

import com.kitsune.core.network.repository.GenerateImageUseCase
import com.kitsune.core.network.repository.ImageGenerationRefusedException
import com.kitsune.core.network.repository.SoftenImagePromptUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerateNovelCoverUseCaseTest {

    private val generateImageUseCase = mockk<GenerateImageUseCase>()
    private val softenImagePromptUseCase = mockk<SoftenImagePromptUseCase>()

    private val useCase = GenerateNovelCoverUseCase(generateImageUseCase, softenImagePromptUseCase)

    private val coverBytes = byteArrayOf(1, 2, 3)

    @Test
    fun `returns the generated cover bytes on success`() = runTest {
        coEvery { generateImageUseCase(any(), any(), any(), any(), any(), any()) } returns Result.success(listOf(coverBytes))

        val result = useCase(title = "My Story", storySummary = "A tale.", characterContext = "Aria", allowMatureContent = false)

        assertEquals(coverBytes, result)
    }

    @Test
    fun `uses a portrait 3-4 aspect ratio for the cover`() = runTest {
        val ratioSlot = slot<String>()
        coEvery {
            generateImageUseCase(any(), any(), any(), any(), capture(ratioSlot), any())
        } returns Result.success(listOf(coverBytes))

        useCase(title = "My Story", storySummary = "", characterContext = "Aria", allowMatureContent = false)

        assertEquals("3:4", ratioSlot.captured)
    }

    @Test
    fun `tags the cover generation with the NOVEL_COVER operation type so it isn't billed like a normal image`() = runTest {
        val operationTypeSlot = slot<String>()
        coEvery {
            generateImageUseCase(any(), any(), any(), any(), any(), capture(operationTypeSlot))
        } returns Result.success(listOf(coverBytes))

        useCase(title = "My Story", storySummary = "", characterContext = "Aria", allowMatureContent = false)

        assertEquals("NOVEL_COVER", operationTypeSlot.captured)
    }

    @Test
    fun `retries with a softened prompt when the model refuses`() = runTest {
        coEvery {
            generateImageUseCase(any(), any(), any(), match<Boolean> { it }, any(), any())
        } returns Result.failure(ImageGenerationRefusedException("refused"))
        coEvery { softenImagePromptUseCase(any(), any()) } returns Result.success("a softened prompt")
        coEvery {
            generateImageUseCase(any(), match<String> { it == "a softened prompt" }, any(), match<Boolean> { !it }, any(), any())
        } returns Result.success(listOf(coverBytes))

        val result = useCase(title = "My Story", storySummary = "Dark tale.", characterContext = "Aria", allowMatureContent = true)

        assertEquals(coverBytes, result)
    }

    @Test
    fun `returns null rather than throwing when the refusal retry also fails`() = runTest {
        coEvery {
            generateImageUseCase(any(), any(), any(), any(), any(), any())
        } returns Result.failure(ImageGenerationRefusedException("refused"))
        coEvery { softenImagePromptUseCase(any(), any()) } returns Result.success("a softened prompt")
        coEvery {
            generateImageUseCase(any(), match<String> { it == "a softened prompt" }, any(), any(), any(), any())
        } returns Result.failure(ImageGenerationRefusedException("refused again"))

        val result = useCase(title = "My Story", storySummary = "", characterContext = "Aria", allowMatureContent = false)

        assertNull(result)
    }

    @Test
    fun `returns null without retrying on a non-refusal failure`() = runTest {
        coEvery { generateImageUseCase(any(), any(), any(), any(), any(), any()) } returns Result.failure(RuntimeException("network error"))

        val result = useCase(title = "My Story", storySummary = "", characterContext = "Aria", allowMatureContent = false)

        assertNull(result)
    }
}
