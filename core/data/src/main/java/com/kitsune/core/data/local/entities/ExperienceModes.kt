package com.kitsune.core.data.local.entities

/** Overall narrative structure/pacing template for a chat ("modes d'expérience"). Independent of
 * [NarrativeRhythmMode], which governs scene-to-scene pacing rather than the story's macro shape. */
enum class StoryPaceMode {
    DEFAULT,
    SLICE_OF_LIFE,
    NEW_ADVENTURE,
    SAGA,
    EPISODIC
}

/** Emotional tone/mood template for a chat. */
enum class ToneMode {
    DEFAULT,
    SOFT_ROMANTIC,
    DRAMATIC_ROMANCE,
    DARK_PSYCHOLOGICAL,
    COMEDY
}

/** How much the AI narrates on its own vs. hands control/choices back to the user. */
enum class InvolvementMode {
    DEFAULT,
    READER,
    CO_AUTHOR,
    IMMERSIVE_RP
}

/** Scene-to-scene pacing, independent of the overall story structure ([StoryPaceMode]). */
enum class NarrativeRhythmMode {
    DEFAULT,
    SLOW_BURN,
    FAST_PACED,
    CONTEMPLATIVE
}

/** How freely the story can change setting/world. */
enum class UniverseExperienceMode {
    DEFAULT,
    FIXED_SETTING,
    MULTIVERSE,
    CAMPAIGN
}

/** How explicit mature content should read. Independent of whether mature content is allowed at
 * all in the first place — that's still governed by [MaturityTag] and server-side moderation. */
enum class IntensityMode {
    DEFAULT,
    SOFT_SUGGESTIVE,
    INTENSE_MATURE
}

/**
 * How long a reply should run (2026-08-23).
 *
 * The six modes above shape *what* happens; this shapes the single thing a reader notices first.
 * Until now the only word count anywhere in the app was the one buried in the fast-paced pacing
 * directive, and `max_tokens` was a flat 4096 (6144 in Pro) — a ceiling so high it never bound
 * anything. This drives **both** the contract line and the real `max_tokens`, so a short reply is
 * short because the budget says so, not only because the prose was asked nicely.
 */
enum class ReplyLengthMode {
    DEFAULT,
    BRIEF,
    MEDIUM,
    LONG,
    UNBOUNDED
}

/**
 * How much of a reply is spoken dialogue versus narration (2026-08-23).
 *
 * The other axis readers notice immediately, and the one that separates a chat that reads like a
 * conversation from one that reads like a novel. Deliberately separate from [ReplyLengthMode]: a
 * long reply can be mostly dialogue, and a short one mostly narration.
 */
enum class NarrationBalanceMode {
    DEFAULT,
    MOSTLY_DIALOGUE,
    BALANCED,
    MOSTLY_NARRATION
}

/**
 * Grammatical person and tense of the narration (2026-08-23).
 *
 * Changes the feel of a story more than any other single setting — the paid Visual Novel style pack
 * is essentially this one choice ("SECOND PERSON, present tense") wrapped in atmosphere. Offering it
 * for free is deliberate: the packs keep their value as full authored registers, while the plain
 * knob stops being something only a purchase can reach.
 */
enum class VoiceMode {
    DEFAULT,
    SECOND_PRESENT,
    THIRD_PAST,
    THIRD_PRESENT,
    FIRST_PAST
}
