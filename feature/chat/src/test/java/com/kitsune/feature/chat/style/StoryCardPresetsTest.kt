package com.kitsune.feature.chat.style

import com.kitsune.core.data.local.entities.IntensityMode
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.ReplyLengthMode
import com.kitsune.core.data.local.entities.ToneCardEntity
import com.kitsune.core.data.local.entities.ToneMode
import com.kitsune.core.network.repository.SamplingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The presets are the whole point of the story card: if two of them produce the same instructions,
 * the card is a menu of one option wearing six hats. These tests pin the properties that make a
 * preset worth tapping — it reaches the model, it differs from its neighbours, and it cannot be used
 * to walk around the maturity gate.
 */
class StoryCardPresetsTest {

    private fun contractFor(preset: StoryPreset): String = buildStyleContract(
        StyleSettings(
            enhancedCraft = false,
            storyPaceMode = preset.storyPaceMode,
            toneMode = preset.toneMode,
            involvementMode = preset.involvementMode,
            narrativeRhythmMode = preset.narrativeRhythmMode,
            universeMode = preset.universeMode,
            intensityMode = preset.intensityMode,
            customDirective = "",
            replyLength = preset.replyLength,
            narrationBalance = preset.narrationBalance,
            voiceMode = preset.voiceMode
        )
    )

    @Test
    fun `every preset actually instructs the model`() {
        // The bug this whole feature exists to fix was a contract carrying nothing but its default
        // register, so "produces a contract" is not enough — it must carry real directives.
        StoryCardPresets.all.forEach { preset ->
            val contract = contractFor(preset)
            assertTrue(
                "${preset.id} must carry mode directives, not just the default register",
                contract.contains("Reply length:") && contract.contains("Balance:") && contract.contains("Voice:")
            )
        }
    }

    @Test
    fun `no two presets produce the same instructions`() {
        val contracts = StoryCardPresets.all.map { contractFor(it) }
        assertEquals("presets that read identically to the model are not real choices", contracts.size, contracts.toSet().size)
    }

    @Test
    fun `ids are unique and resolvable`() {
        val ids = StoryCardPresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertNotNull(it, StoryCardPresets.byId(it)) }
    }

    @Test
    fun `an unknown id resolves to nothing rather than to a wrong preset`() {
        assertEquals(null, StoryCardPresets.byId("no_such_preset"))
    }

    // --- The maturity gate ---

    @Test
    fun `mature presets are hidden from a persona that is not tagged for them`() {
        val offered = StoryCardPresets.available(allowMature = false)
        assertTrue(offered.isNotEmpty())
        assertTrue("the card must not be a way around the gate", offered.none { it.requiresMature })
    }

    @Test
    fun `mature presets are offered when the persona is tagged`() {
        assertTrue(StoryCardPresets.available(allowMature = true).any { it.requiresMature })
    }

    @Test
    fun `no non-mature preset asks for explicit content`() {
        StoryCardPresets.available(allowMature = false).forEach {
            assertFalse(
                "${it.id} would request explicit prose without the tag that permits it",
                it.intensityMode == IntensityMode.INTENSE_MATURE
            )
        }
    }

    @Test
    fun `allowMature follows the same tags the experience dialog checks`() {
        assertTrue(StoryCardPresets.allowMature(listOf(MaturityTag.NSFW)))
        assertTrue(StoryCardPresets.allowMature(listOf(MaturityTag.DARK)))
        assertFalse(StoryCardPresets.allowMature(listOf(MaturityTag.SFW)))
        assertFalse(StoryCardPresets.allowMature(emptyList()))
    }

    // --- Skipping ---

    @Test
    fun `skipping still applies a real preset`() {
        // If "Skip" left the chat at all-DEFAULT it would reintroduce exactly the empty contract this
        // feature removes.
        val skipped = StoryCardPresets.defaultFor(emptyList())
        assertTrue(contractFor(skipped).contains("Reply length:"))
    }

    @Test
    fun `the skip default is safe for an untagged persona`() {
        val skipped = StoryCardPresets.defaultFor(listOf(MaturityTag.SFW))
        assertFalse("skipping must never opt a plain persona into mature content", skipped.requiresMature)
    }

    @Test
    fun `a dark persona skips into a register that suits it`() {
        assertEquals(StoryCardPresets.DARK_PASSION, StoryCardPresets.defaultFor(listOf(MaturityTag.DARK)).id)
    }

    // --- Sampling profiles ---

    @Test
    fun `penalties stay inside the range where prose survives`() {
        // Tightened on 2026-09-01 from a reasoned 0.6 to a measured 0.3: a real transcript came back
        // as multilingual token soup, which is what a penalty looks like once it stops discouraging
        // repeated imagery and starts discouraging the function words that hold a sentence together.
        // This bound is the guard against quietly drifting back up.
        StoryCardPresets.all.forEach { preset ->
            preset.sampling.frequencyPenalty?.let {
                assertTrue("${preset.id} frequency penalty out of range: $it", it in 0.0..0.3)
            }
            preset.sampling.presencePenalty?.let {
                assertTrue("${preset.id} presence penalty out of range: $it", it in 0.0..0.3)
            }
            preset.sampling.temperature?.let {
                assertTrue("${preset.id} temperature out of range: $it", it in 0.5..1.0)
            }
            preset.sampling.topP?.let {
                assertTrue("${preset.id} top_p out of range: $it", it in 0.5..1.0)
            }
        }
    }

    @Test
    fun `every preset states an anti-repetition penalty`() {
        // The reason this feature exists: nothing in the app ever sent one, and replies started
        // echoing themselves after a few dozen turns.
        StoryCardPresets.all.forEach { preset ->
            assertNotNull(
                "${preset.id} would run with no repetition control at all",
                preset.sampling.frequencyPenalty ?: preset.sampling.presencePenalty
            )
        }
    }

    @Test
    fun `comedy decodes hotter than the dark register`() {
        // Not decoration: an unexpected word is the payload in comedy and a defect in a controlled,
        // obsessive register. If these two ever converge, the sampling profiles have stopped meaning
        // anything.
        val comedy = requireNotNull(StoryCardPresets.byId(StoryCardPresets.LIGHT_COMEDY))
        val dark = requireNotNull(StoryCardPresets.byId(StoryCardPresets.DARK_PASSION))
        assertTrue(requireNotNull(comedy.sampling.temperature) > requireNotNull(dark.sampling.temperature))
        assertEquals(ToneMode.COMEDY, comedy.toneMode)
    }

    // --- Authored tone cards (2026-08-24) ---

    private fun toneCard(
        name: String = "Rebondissements",
        tone: ToneMode = ToneMode.COMEDY,
        intensity: IntensityMode = IntensityMode.DEFAULT,
        base: String = "",
        personaId: String? = null
    ) = ToneCardEntity(
        id = "card-$name",
        personaId = personaId,
        name = name,
        description = "Des intrigues et de nouveaux visages souvent.",
        basePresetId = base,
        toneMode = tone,
        intensityMode = intensity,
        replyLength = ReplyLengthMode.MEDIUM,
        directive = "Introduis un nouveau personnage toutes les quelques scènes.",
        createdAt = 0L
    )

    @Test
    fun `an authored card carries its own name, description and instruction`() {
        // A built-in resolves its label from a string resource; a card written by a person — possibly
        // on another device, shipped through the marketplace — has no resource to resolve against.
        val preset = StoryCardPresets.fromToneCard(toneCard())
        assertEquals("Rebondissements", preset.displayName)
        assertEquals("Des intrigues et de nouveaux visages souvent.", preset.displayDescription)
        assertEquals("Introduis un nouveau personnage toutes les quelques scènes.", preset.directive)
    }

    @Test
    fun `an authored card reaches the model like any preset`() {
        val contract = contractFor(StoryCardPresets.fromToneCard(toneCard()))
        assertTrue(contract.contains("Tone:"))
        assertTrue(contract.contains("Reply length:"))
    }

    @Test
    fun `an authored card persists an id the built-in catalogue can still resolve`() {
        // `storyPresetId` is read on every turn to recover the decoder profile and base temperature.
        // Persisting the card's own id would leave both unresolvable the moment the story started.
        val preset = StoryCardPresets.fromToneCard(toneCard())
        assertTrue("its own id must stay unique per card", StoryCardPresets.isToneCard(preset.id))
        assertNotNull("but what gets stored must resolve", StoryCardPresets.byId(preset.storedPresetId))
    }

    @Test
    fun `an authored card without a base still gets repetition control`() {
        // Derived from its tone rather than borrowed, so a card written before base presets existed —
        // or by someone who never picked one — never decodes with no penalty at all.
        val sampling = StoryCardPresets.fromToneCard(toneCard(base = "")).sampling
        assertNotNull(sampling.frequencyPenalty ?: sampling.presencePenalty)
    }

    @Test
    fun `an unknown base preset falls back rather than losing the profile`() {
        val sampling = StoryCardPresets.fromToneCard(toneCard(base = "from_a_future_version")).sampling
        assertNotNull(sampling.frequencyPenalty ?: sampling.presencePenalty)
    }

    @Test
    fun `an authored card asking for explicit prose is gated like a built-in`() {
        // The profile library is deliberately ungated at authoring time — there is no character to
        // check against there — so the gate has to hold at the point of use, or writing a tone card
        // would be a way around the persona's own maturity tag.
        val explicit = StoryCardPresets.fromToneCard(toneCard(intensity = IntensityMode.INTENSE_MATURE))
        assertTrue("must be recognised as mature", explicit.requiresMature)
    }

    @Test
    fun `a profile card belongs to no persona, so it is never published with one`() {
        // Publishing reads `getByPersona`, which matches on a non-null id: a profile card describes
        // its author's taste, not the character, and must not travel with a listing.
        assertEquals(null, toneCard().personaId)
    }

    @Test
    fun `the inherit profile asks for nothing`() {
        // Conversations created before this feature must keep decoding exactly as they did.
        with(SamplingProfile.INHERIT) {
            assertEquals(null, temperature)
            assertEquals(null, topP)
            assertEquals(null, frequencyPenalty)
            assertEquals(null, presencePenalty)
        }
    }
}
