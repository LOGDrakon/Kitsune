package com.kitsune.feature.chat.style

import com.kitsune.core.data.local.entities.IntensityMode
import com.kitsune.core.data.local.entities.InvolvementMode
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.NarrationBalanceMode
import com.kitsune.core.data.local.entities.NarrativeRhythmMode
import com.kitsune.core.data.local.entities.ReplyLengthMode
import com.kitsune.core.data.local.entities.StoryPaceMode
import com.kitsune.core.data.local.entities.ToneCardEntity
import com.kitsune.core.data.local.entities.ToneMode
import com.kitsune.core.data.local.entities.UniverseExperienceMode
import com.kitsune.core.data.local.entities.VoiceMode
import com.kitsune.core.network.repository.SamplingProfile

/**
 * Named, one-tap recipes for a whole conversation's style — the "carte d'histoire" (2026-08-23).
 *
 * ## Why presets rather than the six chip rows
 *
 * Every knob these set already existed and was already well written. The problem was that reaching
 * them meant opening the ⋮ menu, finding "Modes d'expérience" and making **nine** independent
 * choices whose interactions are not obvious — so in practice nobody did, and
 * [buildStyleContract] ran with nothing but its default register. A preset turns nine decisions into
 * one, and the detailed dialog stays available for anyone who wants to tune afterwards.
 *
 * ## Why each preset also carries sampling
 *
 * Values were **halved on 2026-09-01** after a real transcript came back with multilingual token soup
 * ("...der hover dietary estable fort answer ev年年 millimeter wheel..."). The original band was
 * reasoned about rather than measured, and 0.5-0.6 turned out to be past the point where the penalty
 * stops discouraging repeated imagery and starts discouraging the function words that hold a sentence
 * together. The ceiling here is now 0.3, and `assessReply` catches whatever still slips through.
 *
 * Prose instructions steer *what* the model writes; sampling steers *how it picks words*. A comedy
 * wants surprise and therefore a hotter, more novelty-seeking decode; a dark psychological piece
 * wants control and repetition of motif, so it runs cooler with a stronger frequency penalty. Doing
 * only one of the two is why mode changes felt weaker than they read on paper.
 *
 * ## Pure by design
 *
 * No Android, no I/O, no clock. `feature:chat` has no `ChatViewModel` test, so anything decidable
 * lives here where [StoryCardPresetsTest] can assert on it — the same rule that produced
 * [ChatStyleContract] and `NarrativeDirector`.
 */

/** The nine style settings a preset fixes, plus its decoder profile. */
data class StoryPreset(
    val id: String,
    val storyPaceMode: StoryPaceMode,
    val toneMode: ToneMode,
    val involvementMode: InvolvementMode,
    val narrativeRhythmMode: NarrativeRhythmMode,
    val universeMode: UniverseExperienceMode,
    val intensityMode: IntensityMode,
    val replyLength: ReplyLengthMode,
    val narrationBalance: NarrationBalanceMode,
    val voiceMode: VoiceMode,
    val sampling: SamplingProfile,
    /** True when this preset only makes sense for a persona tagged NSFW or DARK. */
    val requiresMature: Boolean = false,
    /**
     * Set only for a preset built from a user-authored [ToneCardEntity] (2026-08-24).
     *
     * Built-in presets leave these null and resolve their label from [id] against the app's string
     * resources, which is what keeps this catalogue free of Android types and testable on the JVM. A
     * tone card carries its own name because it was written by a person, in their own words, and is
     * shipped to other people through the marketplace — there is no resource to look it up in.
     */
    val displayName: String? = null,
    val displayDescription: String? = null,
    /** The free-text instruction a tone card carries, applied to any story started from it. */
    val directive: String = "",
    /**
     * What to persist in `ChatEntity.storyPresetId`, which is not always [id].
     *
     * A tone card's [id] is unique per card so lists can key on it, but the chat has to store
     * something the built-in catalogue can still resolve later: `storyPresetId` is read on every turn
     * to recover the decoder profile and the base temperature. Storing the card's own id would leave
     * both unresolvable the moment the conversation started — the card would set the modes correctly
     * and then decode as if no preset had been chosen at all.
     */
    val storedPresetId: String = id
)

object StoryCardPresets {

    const val SLOW_ROMANCE = "slow_romance"
    const val DARK_PASSION = "dark_passion"
    const val ADVENTURE = "adventure"
    const val LIGHT_COMEDY = "light_comedy"
    const val LONG_SAGA = "long_saga"
    const val CHAMBER_PIECE = "chamber_piece"

    /** Applied when the user skips the card. Never mature, never surprising — a safe middle. */
    const val FALLBACK = SLOW_ROMANCE

    val all: List<StoryPreset> = listOf(
        // Close, unhurried, emotionally uncertain. Medium replies with an even balance: the mode that
        // most people mean when they say "roleplay".
        StoryPreset(
            id = SLOW_ROMANCE,
            storyPaceMode = StoryPaceMode.SLICE_OF_LIFE,
            toneMode = ToneMode.DRAMATIC_ROMANCE,
            involvementMode = InvolvementMode.IMMERSIVE_RP,
            narrativeRhythmMode = NarrativeRhythmMode.SLOW_BURN,
            universeMode = UniverseExperienceMode.FIXED_SETTING,
            intensityMode = IntensityMode.SOFT_SUGGESTIVE,
            replyLength = ReplyLengthMode.MEDIUM,
            narrationBalance = NarrationBalanceMode.BALANCED,
            voiceMode = VoiceMode.THIRD_PRESENT,
            sampling = SamplingProfile(temperature = 0.9, presencePenalty = 0.25, frequencyPenalty = 0.15)
        ),
        // Cooler decode on purpose: obsession and control read as deliberate when the model is not
        // reaching for novelty, and the higher frequency penalty keeps the recurring motifs this
        // register lives on from collapsing into verbatim repeats.
        StoryPreset(
            id = DARK_PASSION,
            storyPaceMode = StoryPaceMode.SAGA,
            toneMode = ToneMode.DARK_PSYCHOLOGICAL,
            involvementMode = InvolvementMode.IMMERSIVE_RP,
            narrativeRhythmMode = NarrativeRhythmMode.SLOW_BURN,
            universeMode = UniverseExperienceMode.FIXED_SETTING,
            intensityMode = IntensityMode.INTENSE_MATURE,
            replyLength = ReplyLengthMode.LONG,
            narrationBalance = NarrationBalanceMode.MOSTLY_NARRATION,
            voiceMode = VoiceMode.THIRD_PAST,
            sampling = SamplingProfile(temperature = 0.85, topP = 0.95, frequencyPenalty = 0.3, presencePenalty = 0.15),
            requiresMature = true
        ),
        StoryPreset(
            id = ADVENTURE,
            storyPaceMode = StoryPaceMode.NEW_ADVENTURE,
            toneMode = ToneMode.DEFAULT,
            involvementMode = InvolvementMode.CO_AUTHOR,
            narrativeRhythmMode = NarrativeRhythmMode.FAST_PACED,
            universeMode = UniverseExperienceMode.MULTIVERSE,
            intensityMode = IntensityMode.DEFAULT,
            replyLength = ReplyLengthMode.BRIEF,
            narrationBalance = NarrationBalanceMode.MOSTLY_DIALOGUE,
            voiceMode = VoiceMode.SECOND_PRESENT,
            sampling = SamplingProfile(temperature = 0.95, presencePenalty = 0.3, frequencyPenalty = 0.15)
        ),
        // The hottest profile of the six: comedy is the one register where an unexpected word is the
        // point rather than a defect.
        StoryPreset(
            id = LIGHT_COMEDY,
            storyPaceMode = StoryPaceMode.EPISODIC,
            toneMode = ToneMode.COMEDY,
            involvementMode = InvolvementMode.CO_AUTHOR,
            narrativeRhythmMode = NarrativeRhythmMode.FAST_PACED,
            universeMode = UniverseExperienceMode.FIXED_SETTING,
            intensityMode = IntensityMode.DEFAULT,
            replyLength = ReplyLengthMode.BRIEF,
            narrationBalance = NarrationBalanceMode.MOSTLY_DIALOGUE,
            voiceMode = VoiceMode.THIRD_PRESENT,
            sampling = SamplingProfile(temperature = 1.0, presencePenalty = 0.3, frequencyPenalty = 0.15)
        ),
        StoryPreset(
            id = LONG_SAGA,
            storyPaceMode = StoryPaceMode.SAGA,
            toneMode = ToneMode.DRAMATIC_ROMANCE,
            involvementMode = InvolvementMode.READER,
            narrativeRhythmMode = NarrativeRhythmMode.DEFAULT,
            universeMode = UniverseExperienceMode.CAMPAIGN,
            intensityMode = IntensityMode.DEFAULT,
            replyLength = ReplyLengthMode.LONG,
            narrationBalance = NarrationBalanceMode.MOSTLY_NARRATION,
            voiceMode = VoiceMode.THIRD_PAST,
            sampling = SamplingProfile(temperature = 0.9, frequencyPenalty = 0.25, presencePenalty = 0.2)
        ),
        StoryPreset(
            id = CHAMBER_PIECE,
            storyPaceMode = StoryPaceMode.SLICE_OF_LIFE,
            toneMode = ToneMode.DEFAULT,
            involvementMode = InvolvementMode.READER,
            narrativeRhythmMode = NarrativeRhythmMode.CONTEMPLATIVE,
            universeMode = UniverseExperienceMode.FIXED_SETTING,
            intensityMode = IntensityMode.DEFAULT,
            replyLength = ReplyLengthMode.LONG,
            narrationBalance = NarrationBalanceMode.MOSTLY_NARRATION,
            voiceMode = VoiceMode.THIRD_PRESENT,
            sampling = SamplingProfile(temperature = 0.85, topP = 0.95, frequencyPenalty = 0.25, presencePenalty = 0.15)
        )
    )

    fun byId(id: String): StoryPreset? = all.firstOrNull { it.id == id }

    /**
     * Turns a persona's or universe's own tone card into a preset the story card can offer.
     *
     * The nine modes come from the card itself, deliberately: they were stored rather than resolved
     * through [ToneCardEntity.basePresetId] precisely so a card shared through the marketplace reads
     * as its author left it, whatever this install's built-in catalogue looks like.
     *
     * The **decoder profile** is not authored at all: asking someone to pick a frequency penalty is a
     * worse question than any it answers. It is taken from the card's base preset when it names one,
     * and otherwise derived from the tone — the axis it actually depends on, since a comedy wants the
     * model reaching for the unexpected word and a controlled register does not. Either way a card
     * always ends up with repetition control, including one written on a version that had none.
     */
    fun fromToneCard(card: ToneCardEntity): StoryPreset {
        val base = byId(card.basePresetId) ?: byId(FALLBACK)
        return StoryPreset(
            id = TONE_CARD_ID_PREFIX + card.id,
            storedPresetId = base?.id ?: FALLBACK,
            storyPaceMode = card.storyPaceMode,
            toneMode = card.toneMode,
            involvementMode = card.involvementMode,
            narrativeRhythmMode = card.narrativeRhythmMode,
            universeMode = card.universeMode,
            intensityMode = card.intensityMode,
            replyLength = card.replyLength,
            narrationBalance = card.narrationBalance,
            voiceMode = card.voiceMode,
            sampling = base?.sampling ?: samplingForTone(card.toneMode),
            // A card asking for explicit prose is gated exactly like a built-in one, so authoring a
            // tone card cannot become a way past the maturity check.
            requiresMature = card.intensityMode == IntensityMode.INTENSE_MATURE,
            displayName = card.name,
            displayDescription = card.description.takeIf { it.isNotBlank() },
            directive = card.directive
        )
    }

    /**
     * The decoder profile a tone card gets when it names no base preset.
     *
     * Keyed on tone alone rather than on all nine modes: it is the axis that actually decides how much
     * novelty the decode should reach for, and a rule per combination would be nine dimensions of
     * guesswork nobody could verify. Same conservative band as the built-in catalogue — penalties
     * above roughly 0.6 start acting on function words and the prose degrades.
     */
    fun samplingForTone(tone: ToneMode): SamplingProfile = when (tone) {
        ToneMode.COMEDY -> SamplingProfile(temperature = 1.0, presencePenalty = 0.3, frequencyPenalty = 0.15)
        ToneMode.DARK_PSYCHOLOGICAL -> SamplingProfile(temperature = 0.85, topP = 0.95, frequencyPenalty = 0.3, presencePenalty = 0.15)
        ToneMode.SOFT_ROMANTIC, ToneMode.DRAMATIC_ROMANCE ->
            SamplingProfile(temperature = 0.9, presencePenalty = 0.25, frequencyPenalty = 0.15)
        ToneMode.DEFAULT -> SamplingProfile(temperature = 0.9, presencePenalty = 0.25, frequencyPenalty = 0.15)
    }

    /** Marks a preset id as coming from a tone card rather than the built-in catalogue, so
     *  `ChatEntity.storyPresetId` can round-trip either without a second column. */
    const val TONE_CARD_ID_PREFIX = "tone:"

    fun isToneCard(presetId: String): Boolean = presetId.startsWith(TONE_CARD_ID_PREFIX)

    /**
     * The presets offerable for a persona with these maturity tags.
     *
     * Mirrors the gate `ExperienceModeDialog` already applies to the two mature options, so the card
     * cannot be a way around it. Ensemble chats have no single persona and pass their own decision
     * in via [allowMature], exactly as `ChatScreen` does today.
     */
    fun available(allowMature: Boolean): List<StoryPreset> =
        all.filter { allowMature || !it.requiresMature }

    fun allowMature(tags: List<MaturityTag>): Boolean =
        MaturityTag.NSFW in tags || MaturityTag.DARK in tags

    /**
     * What to apply when the user taps "Skip".
     *
     * The point of the card is that a conversation never starts unsteered; skipping must therefore
     * still produce a real preset, not fall back to the all-DEFAULT state the card exists to
     * eliminate. Chosen from the persona's own tags so the guess is at least informed: a story whose
     * character is tagged for dark content gets the register that fits it.
     */
    fun defaultFor(tags: List<MaturityTag>): StoryPreset {
        val id = if (MaturityTag.DARK in tags) DARK_PASSION else FALLBACK
        return checkNotNull(byId(id)) { "preset $id is missing from the catalogue" }
    }
}
