package com.kitsune.core.data.local.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tone card is authored once and then travels — through an export file, and through the
 * marketplace into other people's installs. A field silently dropped in transit is invisible to the
 * author (their copy is fine) and unfixable by the recipient (they never saw the original), so the
 * round trip is the thing worth pinning.
 */
class ToneCardPayloadTest {

    private fun card(id: String = "card-1") = ToneCardEntity(
        id = id,
        personaId = "persona-1",
        name = "Rivalité de bureau",
        description = "Deux collègues qui refusent de l'admettre.",
        basePresetId = "light_comedy",
        storyPaceMode = StoryPaceMode.EPISODIC,
        toneMode = ToneMode.COMEDY,
        involvementMode = InvolvementMode.CO_AUTHOR,
        narrativeRhythmMode = NarrativeRhythmMode.FAST_PACED,
        universeMode = UniverseExperienceMode.FIXED_SETTING,
        intensityMode = IntensityMode.SOFT_SUGGESTIVE,
        replyLength = ReplyLengthMode.BRIEF,
        narrationBalance = NarrationBalanceMode.MOSTLY_DIALOGUE,
        voiceMode = VoiceMode.THIRD_PRESENT,
        directive = "Elle ne cède jamais la dernière réplique.",
        createdAt = 42L
    )

    @Test
    fun `every authored field survives a full round trip`() {
        val original = card()
        val restored = decodeToneCards(encodeToneCards(listOf(original)))
            .single()
            .toEntity(personaId = "persona-on-the-other-side")

        assertEquals(original.name, restored.name)
        assertEquals(original.description, restored.description)
        assertEquals(original.basePresetId, restored.basePresetId)
        assertEquals(original.storyPaceMode, restored.storyPaceMode)
        assertEquals(original.toneMode, restored.toneMode)
        assertEquals(original.involvementMode, restored.involvementMode)
        assertEquals(original.narrativeRhythmMode, restored.narrativeRhythmMode)
        assertEquals(original.universeMode, restored.universeMode)
        assertEquals(original.intensityMode, restored.intensityMode)
        assertEquals(original.replyLength, restored.replyLength)
        assertEquals(original.narrationBalance, restored.narrationBalance)
        assertEquals(original.voiceMode, restored.voiceMode)
        assertEquals(original.directive, restored.directive)
    }

    @Test
    fun `the receiving install owns the identity and the scope`() {
        // Carrying the sender's ids across would attach the card to a persona row that does not exist
        // here, and a duplicate import would collide on the primary key.
        val restored = decodeToneCards(encodeToneCards(listOf(card())))
            .single()
            .toEntity(personaId = "persona-on-the-other-side")

        assertEquals("persona-on-the-other-side", restored.personaId)
        assertNull(restored.universeId)
        assertNotEquals("card-1", restored.id)
    }

    @Test
    fun `two imports of the same card do not collide`() {
        val payload = decodeToneCards(encodeToneCards(listOf(card()))).single()
        assertNotEquals(
            payload.toEntity(personaId = "p").id,
            payload.toEntity(personaId = "p").id
        )
    }

    @Test
    fun `a universe-scoped card keeps its scope`() {
        val restored = decodeToneCards(encodeToneCards(listOf(card()))).single()
            .toEntity(universeId = "universe-1")
        assertEquals("universe-1", restored.universeId)
        assertNull(restored.personaId)
    }

    @Test
    fun `several cards survive together, in order`() {
        val cards = listOf(card("a").copy(name = "A"), card("b").copy(name = "B"))
        assertEquals(listOf("A", "B"), decodeToneCards(encodeToneCards(cards)).map { it.name })
    }

    // --- Absence and damage ---

    @Test
    fun `a persona with no tones carries no field at all`() {
        // Rather than an empty array, so a persona that has none looks exactly as it did before tone
        // cards existed.
        assertNull(encodeToneCards(emptyList()))
    }

    @Test
    fun `a listing published before tone cards existed decodes to none`() {
        assertTrue(decodeToneCards(null).isEmpty())
        assertTrue(decodeToneCards("").isEmpty())
        assertTrue(decodeToneCards("   ").isEmpty())
    }

    @Test
    fun `a damaged blob loses the tones, never the persona`() {
        // Losing the tones is recoverable by hand; failing the whole import over them is not.
        assertTrue(decodeToneCards("{not json").isEmpty())
        assertTrue(decodeToneCards("[{\"unexpected\":1}]").isEmpty())
    }

    @Test
    fun `an unknown mode degrades to the default rather than refusing to import`() {
        // A card authored on a newer version naming a mode this build has never heard of.
        val json = """[{"name":"Futur","toneMode":"SOMETHING_NEW","replyLength":"EPIC"}]"""
        val restored = decodeToneCards(json).single().toEntity(personaId = "p")
        assertEquals("Futur", restored.name)
        assertEquals(ToneMode.DEFAULT, restored.toneMode)
        assertEquals(ReplyLengthMode.DEFAULT, restored.replyLength)
    }

    @Test
    fun `a card that only names itself still imports`() {
        val restored = decodeToneCards("""[{"name":"Minimal"}]""").single().toEntity(personaId = "p")
        assertEquals("Minimal", restored.name)
        assertEquals("", restored.directive)
        assertEquals(StoryPaceMode.DEFAULT, restored.storyPaceMode)
    }
}
