package com.kitsune.core.network.repository

import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.security.locale.AppLanguage
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.profile.UserProfileStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateWorldElementUseCaseTest {

    private val chatCompletionRepository = mockk<ChatCompletionRepository>()
    private val llmModelResolver = mockk<LlmModelResolver> {
        coEvery { resolve(any()) } returns "gpt-4.1"
    }
    private val userProfileStore = mockk<UserProfileStore> {
        every { get() } returns com.kitsune.core.security.profile.UserProfile()
    }
    private val appLanguageManager = mockk<AppLanguageManager> {
        every { getSelectedLanguage() } returns AppLanguage.ENGLISH
    }

    private val useCase = GenerateWorldElementUseCase(chatCompletionRepository, llmModelResolver, userProfileStore, appLanguageManager)

    private fun bundleJson(factionCount: Int = 2, locationCount: Int = 2, npcCount: Int = 3) = """
        {
          "name": "Aurelia", "description": "A floating city-state.", "genre": "fantasy", "visualStyle": "painterly",
          "factions": [${(1..factionCount).joinToString(",") { "{\"name\":\"Faction $it\",\"description\":\"d\",\"type\":\"guild\",\"alignment\":\"neutral\"}" }}],
          "locations": [${(1..locationCount).joinToString(",") { "{\"name\":\"Location $it\",\"description\":\"d\",\"type\":\"city\"}" }}],
          "npcs": [${(1..npcCount).joinToString(",") { "{\"name\":\"Npc $it\",\"description\":\"d\",\"personality\":\"p\",\"role\":\"role\"}" }}]
        }
    """.trimIndent()

    @Test
    fun `parses the universe fields from a complete bundle response`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = bundleJson(), usage = null, modelUsed = "x")
        )

        val bundle = useCase.generateUniverseBundle("a floating city").getOrThrow()

        assertEquals("Aurelia", bundle.universe.name)
        assertEquals("fantasy", bundle.universe.genre)
    }

    @Test
    fun `parses exactly the nested factions, locations and npcs arrays`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(content = bundleJson(), usage = null, modelUsed = "x")
        )

        val bundle = useCase.generateUniverseBundle("a floating city").getOrThrow()

        assertEquals(2, bundle.factions.size)
        assertEquals(2, bundle.locations.size)
        assertEquals(3, bundle.npcs.size)
        assertEquals("Faction 1", bundle.factions.first().name)
        assertEquals("Location 1", bundle.locations.first().name)
        assertEquals("Npc 1", bundle.npcs.first().name)
    }

    @Test
    fun `falls back to the surprise-me prompt when the description is blank`() = runTest {
        val messagesSlot = slot<List<ChatTurn>>()
        coEvery {
            chatCompletionRepository.complete(any(), any(), capture(messagesSlot), any(), any(), any(), any(), any(), any())
        } returns Result.success(ChatCompletionResult(content = bundleJson(), usage = null, modelUsed = "x"))

        useCase.generateUniverseBundle("")

        assertTrue(messagesSlot.captured.single().content.contains("Surprise me"))
    }

    @Test
    fun `returns empty nested lists rather than failing when the arrays are missing`() = runTest {
        coEvery { chatCompletionRepository.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            ChatCompletionResult(
                content = """{"name": "Aurelia", "description": "d", "genre": "g", "visualStyle": "v"}""",
                usage = null,
                modelUsed = "x"
            )
        )

        val bundle = useCase.generateUniverseBundle("a floating city").getOrThrow()

        assertTrue(bundle.factions.isEmpty())
        assertTrue(bundle.locations.isEmpty())
        assertTrue(bundle.npcs.isEmpty())
    }
}
