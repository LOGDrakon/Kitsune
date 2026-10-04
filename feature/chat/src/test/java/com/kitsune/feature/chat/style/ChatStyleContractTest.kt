package com.kitsune.feature.chat.style

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import com.kitsune.core.data.local.entities.IntensityMode
import com.kitsune.core.data.local.entities.InvolvementMode
import com.kitsune.core.data.local.entities.NarrationBalanceMode
import com.kitsune.core.data.local.entities.NarrativeRhythmMode
import com.kitsune.core.data.local.entities.ReplyLengthMode
import com.kitsune.core.data.local.entities.StoryPaceMode
import com.kitsune.core.data.local.entities.ToneMode
import com.kitsune.core.data.local.entities.UniverseExperienceMode
import com.kitsune.core.data.local.entities.VoiceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole point of this module is that a mode change must be *visible* in what the model is told.
 * These tests pin the two properties that make that true — the contract actually carries every
 * selected mode, and a pivot names both sides of the change plus the clause that breaks style
 * inertia — neither of which is observable from the app without sending real messages.
 */
class ChatStyleContractTest {

    /** JUnit's own assertNotNull returns Unit; this returns the value so it can be asserted on. */
    private fun <T : Any> requireNotNull(value: T?, message: String = "expected non-null"): T {
        assertNotNull(message, value)
        return value!!
    }

    private fun settings(
        enhancedCraft: Boolean = false,
        storyPace: StoryPaceMode = StoryPaceMode.DEFAULT,
        tone: ToneMode = ToneMode.DEFAULT,
        involvement: InvolvementMode = InvolvementMode.DEFAULT,
        rhythm: NarrativeRhythmMode = NarrativeRhythmMode.DEFAULT,
        universe: UniverseExperienceMode = UniverseExperienceMode.DEFAULT,
        intensity: IntensityMode = IntensityMode.DEFAULT,
        custom: String = "",
        replyLength: ReplyLengthMode = ReplyLengthMode.DEFAULT,
        balance: NarrationBalanceMode = NarrationBalanceMode.DEFAULT,
        voice: VoiceMode = VoiceMode.DEFAULT,
        stylePack: String = "",
        globalStyle: String = "",
        neverWrite: String = ""
    ) = StyleSettings(
        enhancedCraft, storyPace, tone, involvement, rhythm, universe, intensity, custom,
        replyLength, balance, voice, stylePack, globalStyle, neverWrite
    )

    // --- Contract: presence and absence ---

    @Test
    fun `an unconfigured chat still gets the default prose register`() {
        // This used to return null, which meant the app's most influential prompt slot sat empty for
        // exactly the conversations nobody had configured — i.e. almost all of them. The register is
        // not new spend: it used to be sent every turn in the system prompt instead.
        val contract = buildStyleContract(settings())
        assertTrue(contract.contains("Default prose register"))
        assertFalse("nothing was selected, so no mode directive belongs here", contract.contains("Tone:"))
    }

    @Test
    fun `craft requirements alone produce a contract`() {
        val contract = requireNotNull(buildStyleContract(settings(enhancedCraft = true)))
        assertTrue(contract.contains("Craft requirements. These are hard constraints"))
    }

    @Test
    fun `a single experience mode alone produces a contract`() {
        val contract = requireNotNull(
            buildStyleContract(settings(rhythm = NarrativeRhythmMode.FAST_PACED))
        )
        assertTrue("the checkable constraint must survive into the contract", contract.contains("120 words"))
    }

    @Test
    fun `the contract always tells the model not to imitate the transcript`() {
        val contract = requireNotNull(buildStyleContract(settings(enhancedCraft = true)))
        assertTrue(
            "without this clause the transcript's 40 counter-examples win over the instruction",
            contract.contains("do not copy") && contract.contains("length, rhythm or register")
        )
    }

    @Test
    fun `contracts with and without craft requirements differ`() {
        val base = settings(tone = ToneMode.COMEDY)
        assertTrue(buildStyleContract(base) != buildStyleContract(base.copy(enhancedCraft = true)))
    }

    // --- Contract: every category is carried ---

    @Test
    fun `every non-default mode in every category contributes a directive`() {
        val all = settings(
            storyPace = StoryPaceMode.SAGA,
            tone = ToneMode.DARK_PSYCHOLOGICAL,
            involvement = InvolvementMode.IMMERSIVE_RP,
            rhythm = NarrativeRhythmMode.SLOW_BURN,
            universe = UniverseExperienceMode.CAMPAIGN,
            intensity = IntensityMode.INTENSE_MATURE
        )
        val contract = requireNotNull(buildStyleContract(all))
        listOf("Macro-structure:", "Tone:", "Involvement:", "Pacing:", "Setting:", "Maturity:")
            .forEach { assertTrue("missing directive for $it", contract.contains(it)) }
    }

    @Test
    fun `a category left at default contributes nothing`() {
        val contract = requireNotNull(buildStyleContract(settings(tone = ToneMode.COMEDY)))
        assertFalse(contract.contains("Setting:"))
        assertFalse(contract.contains("Maturity:"))
    }

    // --- Contract: the free-text directive and its precedence ---

    @Test
    fun `the custom directive is carried verbatim and marked highest priority`() {
        val contract = requireNotNull(
            buildStyleContract(settings(custom = "Write in the first person, past tense."))
        )
        assertTrue(contract.contains("Write in the first person, past tense."))
        assertTrue(contract.contains("follow it above all else"))
    }

    @Test
    fun `precedence is stated when the custom directive could conflict with a preset`() {
        val contract = requireNotNull(
            buildStyleContract(settings(rhythm = NarrativeRhythmMode.FAST_PACED, custom = "Be slow."))
        )
        val custom = contract.indexOf("Be slow.")
        val preset = contract.indexOf("Pacing:")
        assertTrue("conflicting sources need an explicit ordering", contract.contains("this order wins"))
        assertTrue("the user's own instruction must come before the presets", custom in 0..<preset)
    }

    @Test
    fun `a blank custom directive is ignored`() {
        val contract = buildStyleContract(settings(custom = "   \n  "))
        assertFalse(contract.contains("follow it above all else"))
    }

    // --- Pivot ---

    @Test
    fun `no pivot when nothing changed`() {
        val s = settings(tone = ToneMode.COMEDY)
        assertNull("opening and closing the dialog without edits must leave no trace", buildStylePivot(s, s))
    }

    @Test
    fun `a pivot names both the previous and the new setting`() {
        val pivot = requireNotNull(
            buildStylePivot(
                settings(rhythm = NarrativeRhythmMode.SLOW_BURN),
                settings(rhythm = NarrativeRhythmMode.FAST_PACED)
            )
        )
        assertTrue(pivot.contains("Slow burn -> Fast-paced"))
    }

    @Test
    fun `a pivot carries the anti-imitation clause`() {
        val pivot = requireNotNull(
            buildStylePivot(settings(), settings(tone = ToneMode.COMEDY))
        )
        assertTrue(
            "this clause is the load-bearing part: it demotes the replies written under the old setting",
            pivot.contains("Do NOT imitate") && pivot.contains("no longer the reference")
        )
    }

    @Test
    fun `changing several settings at once produces one pivot listing them all`() {
        val pivot = requireNotNull(
            buildStylePivot(
                settings(),
                settings(enhancedCraft = true, tone = ToneMode.COMEDY, rhythm = NarrativeRhythmMode.FAST_PACED)
            )
        )
        assertTrue(pivot.contains("Craft requirements: off -> on"))
        assertTrue(pivot.contains("Tone: none -> Comedy"))
        assertTrue(pivot.contains("Pacing: none -> Fast-paced"))
        assertEquals("one marker, not one per changed setting", 1, pivot.split("[STYLE CHANGE").size - 1)
    }

    @Test
    fun `switching a category back to default is itself a change worth signalling`() {
        val pivot = requireNotNull(
            buildStylePivot(settings(tone = ToneMode.COMEDY), settings())
        )
        assertTrue(pivot.contains("Tone: Comedy -> none"))
    }

    @Test
    fun `editing the custom directive produces a pivot quoting the new text`() {
        val pivot = requireNotNull(
            buildStylePivot(settings(), settings(custom = "Never describe clothing."))
        )
        assertTrue(pivot.contains("Never describe clothing."))
    }

    @Test
    fun `clearing the custom directive is signalled as a removal`() {
        val pivot = requireNotNull(
            buildStylePivot(settings(custom = "Never describe clothing."), settings())
        )
        assertTrue(pivot.contains("was removed"))
    }

    @Test
    fun `whitespace-only edits to the custom directive are not a change`() {
        assertNull(buildStylePivot(settings(custom = "Be brief."), settings(custom = "  Be brief.  ")))
    }


    // --- Reply shape: the three settings a reader notices first ---

    @Test
    fun `reply length, balance and voice each reach the model`() {
        val contract = buildStyleContract(
            settings(
                replyLength = ReplyLengthMode.BRIEF,
                balance = NarrationBalanceMode.MOSTLY_DIALOGUE,
                voice = VoiceMode.SECOND_PRESENT
            )
        )
        assertTrue(contract.contains("Reply length:"))
        assertTrue(contract.contains("Balance:"))
        assertTrue(contract.contains("Voice:"))
    }

    @Test
    fun `each reply length states a different target`() {
        // A length setting that does not change the number the model is given is decoration.
        val targets = listOf(ReplyLengthMode.BRIEF, ReplyLengthMode.MEDIUM, ReplyLengthMode.LONG)
            .map { buildStyleContract(settings(replyLength = it)) }
        assertEquals("each length must be distinguishable", 3, targets.toSet().size)
        assertTrue(targets[0].contains("120 words"))
        assertTrue(targets[1].contains("250 words"))
        assertTrue(targets[2].contains("400 words"))
    }

    @Test
    fun `the voice directive forbids the persons it is not`() {
        // Second-person narration is the single change that most alters how a story reads, and the
        // one a model drifts out of fastest — so the directive has to be exclusive, not suggestive.
        val third = buildStyleContract(settings(voice = VoiceMode.THIRD_PAST))
        assertTrue(third.contains("never \"you\""))
    }

    // --- Style packs: rank 3, and replacing rather than fighting the default register ---

    @Test
    fun `a style pack replaces the default register instead of stacking on it`() {
        val pack = "Write as a stage play. CHARACTER NAME: \"their line\"."
        val contract = buildStyleContract(settings(stylePack = pack))
        assertTrue("the pack must be present", contract.contains(pack))
        assertFalse(
            "the default register would contradict the pack it is supposed to yield to",
            contract.contains("Default prose register")
        )
    }

    @Test
    fun `the pack is ranked below this conversation's own instruction`() {
        val contract = buildStyleContract(
            settings(custom = "Keep her silent.", stylePack = "Write as a stage play.")
        )
        assertTrue(contract.indexOf("Keep her silent.") < contract.indexOf("Write as a stage play."))
        assertTrue(contract.contains("this order wins"))
    }

    @Test
    fun `the global custom style is carried, below the conversation's own instruction`() {
        val contract = buildStyleContract(
            settings(custom = "Keep her silent.", globalStyle = "Write like Murakami.")
        )
        assertTrue(contract.contains("Write like Murakami."))
        assertTrue(contract.indexOf("Keep her silent.") < contract.indexOf("Write like Murakami."))
    }

    // --- The hard-avoid list ---

    @Test
    fun `the never-write list is carried and placed after the presets`() {
        val contract = buildStyleContract(
            settings(tone = ToneMode.COMEDY, neverWrite = "No spiders.")
        )
        assertTrue(contract.contains("No spiders."))
        assertTrue(
            "placed before the presets it would read as just another preference",
            contract.indexOf("Tone:") < contract.indexOf("No spiders.")
        )
    }

    @Test
    fun `a blank never-write list adds no heading`() {
        assertFalse(buildStyleContract(settings(neverWrite = "  ")).contains("Never write this"))
    }

    // --- Pivot coverage for the new settings ---

    @Test
    fun `changing reply shape produces a pivot`() {
        val pivot = requireNotNull(
            buildStylePivot(settings(), settings(replyLength = ReplyLengthMode.BRIEF, voice = VoiceMode.FIRST_PAST))
        )
        assertTrue(pivot.contains("Reply length:"))
        assertTrue(pivot.contains("Voice:"))
    }

    @Test
    fun `the pivot names a pack change without restating the pack`() {
        // A pack runs to a dozen lines; echoing it into the transcript would spend the raw window on
        // instructions the contract already re-sends every turn.
        val pack = "Write as a stage play with stage directions in parentheses."
        val pivot = requireNotNull(buildStylePivot(settings(), settings(stylePack = pack)))
        assertTrue(pivot.contains("Writing style pack"))
        assertFalse(pivot.contains(pack))
    }

    // --- Moderation safety of the app's own boilerplate ---

    /**
     * Mirrors the indicators in `ModerationService` (minor-age terms and the article + "minor"
     * regex) that, co-occurring with an explicit sexual term anywhere in the last ten messages,
     * put a chat in front of the blocking classifier.
     */
    private val forbiddenInAppText = listOf(
        Regex("""\b(?:a|an|the|this|that|these|those|her|his|their|my|your)\s+minors?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmineure?s?\b""", RegexOption.IGNORE_CASE),
        Regex("""\benfants?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bchild(?:ren)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bkid\s""", RegexOption.IGNORE_CASE),
        Regex("""\bgamins?\b|\bgamines?\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:elementary\s+school|école\s+primaire|toddler|prepubescent)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:sexe|porn|nude|fuck|orgasm|orgasme|masturbat|penetrat|pénétrat|fellatio|fellation)""", RegexOption.IGNORE_CASE)
    )

    /**
     * Regression guard for a real, severe bug (2026-08-16): "a minor disagreement" in the
     * slice-of-life directive matched the minor-age indicator, and because the style contract is
     * re-sent on every single turn it collided permanently with the explicit wording in one user's
     * own writing instruction — hard-blocking their conversation on every message they sent.
     *
     * Every generated directive is checked, not just the one that broke, because the trap catches
     * ordinary English ("a minor detail", "school", "kids") and the next person adding a mode has
     * no reason to suspect it.
     */
    @Test
    fun `no generated directive contains moderation trigger vocabulary`() {
        val everyDirective = buildList {
            StoryPaceMode.entries.forEach { add(buildStyleContract(settings(storyPace = it))) }
            ToneMode.entries.forEach { add(buildStyleContract(settings(tone = it))) }
            InvolvementMode.entries.forEach { add(buildStyleContract(settings(involvement = it))) }
            NarrativeRhythmMode.entries.forEach { add(buildStyleContract(settings(rhythm = it))) }
            UniverseExperienceMode.entries.forEach { add(buildStyleContract(settings(universe = it))) }
            IntensityMode.entries.forEach { add(buildStyleContract(settings(intensity = it))) }
            ReplyLengthMode.entries.forEach { add(buildStyleContract(settings(replyLength = it))) }
            NarrationBalanceMode.entries.forEach { add(buildStyleContract(settings(balance = it))) }
            VoiceMode.entries.forEach { add(buildStyleContract(settings(voice = it))) }
            add(buildStyleContract(settings(enhancedCraft = true)))
            // The default register now travels in the contract too, so it sits in the moderation
            // window every turn exactly like the mode directives do.
            add(buildStyleContract(settings()))
        }

        everyDirective.forEach { text ->
            forbiddenInAppText.forEach { pattern ->
                val hit = pattern.find(text)
                assertNull(
                    "app-generated prompt text must not contain moderation trigger vocabulary, " +
                        "found \"${hit?.value}\" in: ${text.take(160)}",
                    hit
                )
            }
        }
    }

    /** The pivot is built from the same directive strings and is persisted into the transcript,
     * so it stays in the moderation window for as long as the raw window holds it. */
    @Test
    fun `no generated pivot line contains moderation trigger vocabulary`() {
        val pivot = requireNotNull(
            buildStylePivot(
                settings(),
                settings(
                    enhancedCraft = true,
                    storyPace = StoryPaceMode.SLICE_OF_LIFE,
                    tone = ToneMode.DARK_PSYCHOLOGICAL,
                    involvement = InvolvementMode.IMMERSIVE_RP,
                    rhythm = NarrativeRhythmMode.FAST_PACED,
                    universe = UniverseExperienceMode.FIXED_SETTING,
                    intensity = IntensityMode.INTENSE_MATURE
                )
            )
        )
        forbiddenInAppText.forEach { pattern ->
            assertNull("pivot must not contain moderation trigger vocabulary", pattern.find(pivot))
        }
    }

    // --- The ChatEntity mapper, where a field mix-up would be silent ---

    @Test
    fun `from maps every chat field onto the matching setting`() {
        val chat = ChatEntity(
            id = "c1",
            universeId = null,
            personaId = "p1",
            title = "t",
            mode = ChatMode.CHAT,
            storyPaceMode = StoryPaceMode.EPISODIC,
            toneMode = ToneMode.SOFT_ROMANTIC,
            involvementMode = InvolvementMode.READER,
            narrativeRhythmMode = NarrativeRhythmMode.CONTEMPLATIVE,
            universeMode = UniverseExperienceMode.MULTIVERSE,
            intensityMode = IntensityMode.SOFT_SUGGESTIVE,
            customExperienceDirective = "Stay cold.",
            createdAt = 0L,
            updatedAt = 0L
        )

        assertEquals(
            StyleSettings(
                enhancedCraft = true,
                storyPaceMode = StoryPaceMode.EPISODIC,
                toneMode = ToneMode.SOFT_ROMANTIC,
                involvementMode = InvolvementMode.READER,
                narrativeRhythmMode = NarrativeRhythmMode.CONTEMPLATIVE,
                universeMode = UniverseExperienceMode.MULTIVERSE,
                intensityMode = IntensityMode.SOFT_SUGGESTIVE,
                customDirective = "Stay cold."
            ),
            StyleSettings.from(chat, enhancedCraft = true)
        )
    }
}
