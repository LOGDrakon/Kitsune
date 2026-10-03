package com.kitsune.core.memory.recap

import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.PersonaEntity
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "What happened while you were away" is the feature this product is being repositioned around, and
 * it has exactly two ways to fail badly:
 *
 * 1. It fires with nothing to say and invents a plot the next turn has never heard of.
 * 2. It stops being a story and becomes a retention hook — the character waiting, missing, asking
 *    where the player went. That is what every competitor does, and it is the line this must not
 *    cross.
 *
 * Both are decidable without a network call, so both are pinned here.
 */
class GenerateWorldBeatUseCaseTest {

    private val useCase = GenerateWorldBeatUseCase(
        loreEntryRepository = mockk(relaxed = true),
        chatCompletionRepository = mockk(relaxed = true),
        llmModelResolver = mockk(relaxed = true)
    )

    private fun persona(
        desire: String = "",
        fear: String = "",
        moralLine: String = ""
    ) = PersonaEntity(
        id = "p",
        universeId = null,
        name = "Aria",
        shortDescription = "",
        personality = "",
        desire = desire,
        fear = fear,
        moralLine = moralLine,
        scenario = "",
        firstMessage = "",
        exampleDialogues = "",
        age = 30,
        maturityTags = emptyList(),
        visualSheetJson = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun thread(name: String = "la dette") = LoreEntryEntity(
        id = "t-$name",
        chatId = "c",
        entryType = LoreEntryType.THREAD,
        name = name,
        summary = "Elle doit encore de l'argent à quelqu'un.",
        content = "",
        createdAt = 0L,
        updatedAt = 0L
    )

    // --- Rule 3: silence rather than filler ---

    @Test
    fun `a story with nothing established has nothing that could have happened`() {
        // No drive, no open promise: any "event" would be pure invention, and the next turn would
        // have no idea what the player is talking about.
        assertFalse(useCase.hasMaterial(persona(), emptyList()))
        assertFalse(useCase.hasMaterial(null, emptyList()))
    }

    @Test
    fun `a character who wants something can have acted on it`() {
        assertTrue(useCase.hasMaterial(persona(desire = "retrouver son frère"), emptyList()))
    }

    @Test
    fun `a fear or a hard line is enough on its own`() {
        assertTrue(useCase.hasMaterial(persona(fear = "être reconnue"), emptyList()))
        assertTrue(useCase.hasMaterial(persona(moralLine = "ne jamais trahir un allié"), emptyList()))
    }

    @Test
    fun `an unkept promise is enough even with no persona`() {
        // Ensemble scenes have no single protagonist, but they still owe the player their threads.
        assertTrue(useCase.hasMaterial(null, listOf(thread())))
    }

    // --- Rule 2: a story beat, never a leash ---

    @Test
    fun `a beat where the character waits for the player is rejected`() {
        listOf(
            "She had been waiting for you by the window since Tuesday.",
            "He missed you more than he would admit.",
            "Elle t'attend toujours au même endroit.",
            "Il se demandait où tu étais passé."
        ).forEach {
            assertTrue("must be caught: $it", useCase.isPlayerDirected(it))
        }
    }

    @Test
    fun `a real event that happens to involve the player is kept`() {
        // The guard has to be narrow: an event can concern the player without begging for them.
        listOf(
            "Aria sold the pendant and left the city before dawn.",
            "The debt came due, and the collector took the shop instead.",
            "Elle a payé la dette avec l'argent du vol, puis a disparu trois jours."
        ).forEach {
            assertFalse("must not be caught: $it", useCase.isPlayerDirected(it))
        }
    }

    @Test
    fun `a story written in second person can still produce a beat`() {
        // Rejecting every "you" would make the whole feature unavailable to second-person stories,
        // which is one of the app's own supported narration voices.
        assertFalse(useCase.isPlayerDirected("The letter addressed to you sat unopened on the table."))
    }
}
