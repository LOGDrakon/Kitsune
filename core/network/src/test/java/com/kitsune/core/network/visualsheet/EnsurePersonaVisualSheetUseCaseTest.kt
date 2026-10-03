package com.kitsune.core.network.visualsheet

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnsurePersonaVisualSheetUseCaseTest {

    private val generateVisualSheetUseCase = mockk<GenerateVisualSheetUseCase>()
    private val useCase = EnsurePersonaVisualSheetUseCase(generateVisualSheetUseCase)

    private val existingSheet = PersonaVisualSheet(
        physicalTraits = "short blue hair",
        artStyle = "watercolor",
        colorPalette = "blue, white",
        defaultOutfit = "sundress"
    )

    @Test
    fun `keeps the existing sheet unchanged and never calls generation when one already exists`() = runTest {
        val outcome = useCase(
            name = "Mika",
            shortDescription = "A cheerful barista",
            personality = "Warm and talkative",
            existingVisualSheetJson = existingSheet.encode()
        )

        assertFalse(outcome.wasGenerated)
        assertEquals(existingSheet.encode(), outcome.visualSheetJson)
        coVerify(exactly = 0) { generateVisualSheetUseCase(any(), any(), any()) }
    }

    @Test
    fun `generates and encodes a new sheet when there is none yet`() = runTest {
        val generated = PersonaVisualSheet("tall", "anime", "red, black", "trench coat")
        coEvery { generateVisualSheetUseCase("Mika", "A cheerful barista", "Warm and talkative") } returns
            Result.success(generated)

        val outcome = useCase(
            name = "Mika",
            shortDescription = "A cheerful barista",
            personality = "Warm and talkative",
            existingVisualSheetJson = null
        )

        assertTrue(outcome.wasGenerated)
        assertEquals(generated.encode(), outcome.visualSheetJson)
    }

    @Test
    fun `generates a new sheet when the existing one is entirely blank`() = runTest {
        val generated = PersonaVisualSheet("tall", "anime", "red, black", "trench coat")
        coEvery { generateVisualSheetUseCase(any(), any(), any()) } returns Result.success(generated)

        val outcome = useCase(
            name = "Mika",
            shortDescription = "A cheerful barista",
            personality = "",
            existingVisualSheetJson = PersonaVisualSheet.EMPTY.encode()
        )

        assertTrue(outcome.wasGenerated)
        assertEquals(generated.encode(), outcome.visualSheetJson)
    }

    @Test
    fun `falls back to the existing json unchanged when generation fails`() = runTest {
        coEvery { generateVisualSheetUseCase(any(), any(), any()) } returns
            Result.failure(IllegalStateException("network down"))

        val outcome = useCase(
            name = "Mika",
            shortDescription = "A cheerful barista",
            personality = "",
            existingVisualSheetJson = null
        )

        assertFalse(outcome.wasGenerated)
        assertEquals(null, outcome.visualSheetJson)
    }
}
