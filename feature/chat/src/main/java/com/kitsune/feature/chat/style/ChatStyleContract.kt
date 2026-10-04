package com.kitsune.feature.chat.style

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.IntensityMode
import com.kitsune.core.data.local.entities.InvolvementMode
import com.kitsune.core.data.local.entities.NarrationBalanceMode
import com.kitsune.core.data.local.entities.NarrativeRhythmMode
import com.kitsune.core.data.local.entities.ReplyLengthMode
import com.kitsune.core.data.local.entities.StoryPaceMode
import com.kitsune.core.data.local.entities.ToneMode
import com.kitsune.core.data.local.entities.UniverseExperienceMode
import com.kitsune.core.data.local.entities.VoiceMode

/**
 * Everything that governs *how* the model should write, in one immutable snapshot.
 *
 * Extracted from `ChatViewModel` as pure logic so it can be unit-tested on the JVM — `feature:chat`
 * has no `ChatViewModelTest` and the module's established convention is to pull prompt/parsing
 * logic out into top-level functions with their own tests (see `StoryTimeSkipMarker`,
 * `RoleplayTextFormatter`).
 */
data class StyleSettings(
    /** The demanding craft rules ([ENHANCED_CRAFT_DIRECTIVES]) — a user setting, once Pro-only. */
    val enhancedCraft: Boolean,
    val storyPaceMode: StoryPaceMode,
    val toneMode: ToneMode,
    val involvementMode: InvolvementMode,
    val narrativeRhythmMode: NarrativeRhythmMode,
    val universeMode: UniverseExperienceMode,
    val intensityMode: IntensityMode,
    /** Free-form instruction the user typed for this specific conversation. Outranks every
     * preset below — see [buildStyleContract]'s precedence block. */
    val customDirective: String,
    val replyLength: ReplyLengthMode = ReplyLengthMode.DEFAULT,
    val narrationBalance: NarrationBalanceMode = NarrationBalanceMode.DEFAULT,
    val voiceMode: VoiceMode = VoiceMode.DEFAULT,
    /**
     * The authored register of a purchased writing style pack, when one applies to this chat.
     *
     * It **replaces** [BASE_CRAFT] instead of stacking on top of it. That is the whole fix: a pack
     * like Théâtre ("format dialogue as CHARACTER NAME:", "stage directions in parentheses") used to
     * be injected into the system prompt *after* seven default prose rules had already told the model
     * to write like a novelist, so it spent its budget contradicting instructions the app itself had
     * just given.
     */
    val stylePackPrompt: String = "",
    /** The app-wide "custom writing style" from Settings. Rank 3, below this chat's own directive. */
    val globalCustomStyle: String = "",
    /**
     * Hard-avoid list from the taste profile — things this player never wants to read, in any story.
     *
     * Placed after the presets and phrased as an override so it cannot be argued away by a mode.
     * Note it is user free text and therefore carries the same moderation exposure as
     * [customDirective]; see the vocabulary warning above.
     */
    val neverWrite: String = ""
) {
    companion object {
        fun from(
            chat: ChatEntity,
            enhancedCraft: Boolean,
            stylePackPrompt: String = "",
            globalCustomStyle: String = "",
            neverWrite: String = ""
        ) = StyleSettings(
            enhancedCraft = enhancedCraft,
            storyPaceMode = chat.storyPaceMode,
            toneMode = chat.toneMode,
            involvementMode = chat.involvementMode,
            narrativeRhythmMode = chat.narrativeRhythmMode,
            universeMode = chat.universeMode,
            intensityMode = chat.intensityMode,
            customDirective = chat.customExperienceDirective,
            replyLength = chat.replyLength,
            narrationBalance = chat.narrationBalance,
            voiceMode = chat.voiceMode,
            stylePackPrompt = stylePackPrompt,
            globalCustomStyle = globalCustomStyle,
            neverWrite = neverWrite
        )
    }
}

/** A single selected mode: [label] names it in a style-pivot line, [directive] is what the model
 * is actually told to do. DEFAULT values have no spec at all and contribute nothing. */
private data class ModeSpec(val label: String, val directive: String)

/**
 * Directives are written as **checkable constraints** — word counts, structural requirements,
 * explicit ban lists — rather than as descriptions of intent ("keep a brisk rhythm", "make it
 * vivid"). A model can comply with "≤120 words, no more than two sentences of description before
 * an action" and a human can verify it at a glance; it cannot meaningfully comply with an
 * adjective, which is why the previous one-sentence-per-mode directives produced so little visible
 * difference between modes.
 *
 * ## Vocabulary constraint — read before editing any string below
 *
 * Everything here is shipped verbatim to the backend inside the style contract, which sits in the
 * moderation window on **every single turn**. `ModerationService.checkChatContent` blocks a chat
 * when a minor-age indicator co-occurs with an explicit sexual term anywhere in the last ten
 * messages, so a single unlucky word in this file collides with whatever the user legitimately
 * wrote and hard-blocks their conversation on every message, forever.
 *
 * That is not hypothetical: "a minor disagreement" in the slice-of-life directive matched the
 * `(a|an|the|…)\s+minors?` indicator and permanently blocked a user whose own writing instruction
 * contained "sexe" (real bug report, 2026-08-16). **Never** write words like minor / mineur /
 * enfant / child / kid / gamin / school, or explicit sexual vocabulary, into these strings — say
 * the same thing another way. [ChatStyleContractTest] enforces this.
 */
private fun specFor(mode: StoryPaceMode): ModeSpec? = when (mode) {
    StoryPaceMode.DEFAULT -> null
    StoryPaceMode.SLICE_OF_LIFE -> ModeSpec(
        "Slice of life",
        "Macro-structure: slice of life. Keep the stakes domestic and small — a meal, a shared silence, an errand, a small disagreement. Do NOT introduce plot-level threats, villains, prophecies or world-shaking events. If a scene drifts toward high drama, bring it back down to an ordinary moment."
    )
    StoryPaceMode.NEW_ADVENTURE -> ModeSpec(
        "New adventure",
        "Macro-structure: new adventure. Every three or four replies, introduce a concrete new event that changes what the characters are physically doing: an arrival, a discovery, a summons, an accident. Never let two consecutive replies be pure conversation with no external development."
    )
    StoryPaceMode.SAGA -> ModeSpec(
        "Saga",
        "Macro-structure: saga. Every reply must connect to something already established — a named character, an unkept promise, a past choice, a faction. Plant at least one detail per scene that could pay off later. Never resolve a thread in the same scene that introduced it."
    )
    StoryPaceMode.EPISODIC -> ModeSpec(
        "Episodic",
        "Macro-structure: episodic. Shape each scene as a complete episode — a clear situation, a complication, a resolution — then close on exactly one unresolved line that opens the next episode."
    )
}

private fun specFor(mode: ToneMode): ModeSpec? = when (mode) {
    ToneMode.DEFAULT -> null
    ToneMode.SOFT_ROMANTIC -> ModeSpec(
        "Soft romantic",
        "Tone: soft romantic. Favour warmth, reassurance and physical closeness. Any conflict must resolve inside the same scene. Never write betrayal, humiliation, cruelty or threats."
    )
    ToneMode.DRAMATIC_ROMANCE -> ModeSpec(
        "Dramatic romance",
        "Tone: dramatic romance. Every scene must carry one unresolved emotional tension — jealousy, doubt, something left unsaid, a competing loyalty. End replies on emotional uncertainty rather than on reassurance."
    )
    ToneMode.DARK_PSYCHOLOGICAL -> ModeSpec(
        "Dark psychological",
        "Tone: dark psychological. Foreground power imbalance, obsession, control and ambivalence. Show what the character wants alongside what they refuse to admit. Never moralise, and never add a redemptive gloss the scene has not earned."
    )
    ToneMode.COMEDY -> ModeSpec(
        "Comedy",
        "Tone: comedy. Land at least one deliberate comic beat per reply — a misunderstanding, bad timing, an absurd escalation, a self-aware aside. Undercut solemn moments instead of sustaining them."
    )
}

private fun specFor(mode: InvolvementMode): ModeSpec? = when (mode) {
    InvolvementMode.DEFAULT -> null
    InvolvementMode.READER -> ModeSpec(
        "Reader",
        "Involvement: reader. Narrate at length, including other characters' actions and the surrounding world. Do not ask the user what they want to do — end on a narrated beat, never on a question."
    )
    InvolvementMode.CO_AUTHOR -> ModeSpec(
        "Co-author",
        "Involvement: co-author. End every reply by opening two or three clearly distinct directions the story could take, phrased in-fiction as real possibilities — never as a numbered menu or an out-of-character question."
    )
    InvolvementMode.IMMERSIVE_RP -> ModeSpec(
        "Immersive RP",
        "Involvement: immersive RP. Stay entirely in character. No narrator voice, no scene-setting asides, no recap of what just happened. Write only what the character says, does and perceives."
    )
}

private fun specFor(mode: NarrativeRhythmMode): ModeSpec? = when (mode) {
    NarrativeRhythmMode.DEFAULT -> null
    NarrativeRhythmMode.SLOW_BURN -> ModeSpec(
        "Slow burn",
        "Pacing: slow burn. Cover a short span of time in close detail — a single gesture, glance or sentence can carry an entire reply. Advance the situation by at most one small step per reply."
    )
    NarrativeRhythmMode.FAST_PACED -> ModeSpec(
        "Fast-paced",
        "Pacing: fast. Keep the reply to roughly 120 words or fewer. No more than two sentences of description before an action or a line of dialogue. End on an unresolved action. Never recap what just happened, never write an extended scene-setting or introspective paragraph."
    )
    NarrativeRhythmMode.CONTEMPLATIVE -> ModeSpec(
        "Contemplative",
        "Pacing: contemplative. Give most of the reply to sensory detail and interior thought; keep physical events minimal and slow."
    )
}

private fun specFor(mode: UniverseExperienceMode): ModeSpec? = when (mode) {
    UniverseExperienceMode.DEFAULT -> null
    UniverseExperienceMode.FIXED_SETTING -> ModeSpec(
        "Fixed setting",
        "Setting: fixed. Every scene stays in the established location and its immediate surroundings. Do not introduce travel, portals, time jumps or new worlds — deepen what exists instead: its rooms, its routines, the people who pass through it."
    )
    UniverseExperienceMode.MULTIVERSE -> ModeSpec(
        "Multiverse",
        "Setting: multiverse. Transitions between worlds are welcome. Whenever a new world appears, establish it in two or three concrete sensory details before continuing the scene."
    )
    UniverseExperienceMode.CAMPAIGN -> ModeSpec(
        "Campaign",
        "Setting: campaign. Bring already-established NPCs and factions back by name, and give them goals that advance whether or not the user engages with them."
    )
}

private fun specFor(mode: IntensityMode): ModeSpec? = when (mode) {
    IntensityMode.DEFAULT -> null
    IntensityMode.SOFT_SUGGESTIVE -> ModeSpec(
        "Suggestive",
        "Maturity: suggestive. Mature moments are carried by emotion and anticipation and then cut away from — never anatomically described."
    )
    IntensityMode.INTENSE_MATURE -> ModeSpec(
        "Explicit",
        "Maturity: explicit. Mature content between consenting adults is written directly and unambiguously, with the same craft as the rest of the prose. Do not fade to black and do not euphemise."
    )
}

/**
 * Reply shape (2026-08-23) — the three settings a reader registers before any of the six above.
 *
 * These exist because the six modes govern *what happens* and never *what the reply looks like*.
 * The only word count in the whole app used to be the one inside the fast-paced pacing directive,
 * and `max_tokens` was a ceiling so high it never bound anything.
 */
private fun specFor(mode: ReplyLengthMode): ModeSpec? = when (mode) {
    ReplyLengthMode.DEFAULT -> null
    ReplyLengthMode.BRIEF -> ModeSpec(
        "Brief",
        "Reply length: brief. Keep the whole reply to roughly 120 words — one short paragraph, two at the very most."
    )
    ReplyLengthMode.MEDIUM -> ModeSpec(
        "Medium",
        "Reply length: medium. Aim for roughly 250 words, two or three paragraphs."
    )
    ReplyLengthMode.LONG -> ModeSpec(
        "Long",
        "Reply length: long. Aim for roughly 400 words and use the room for detail the scene actually needs. Cut every sentence that only fills space."
    )
    ReplyLengthMode.UNBOUNDED -> ModeSpec(
        "Unconstrained",
        "Reply length: unconstrained. Let the scene decide how long the reply runs, and stop the moment it has nothing left to add."
    )
}

private fun specFor(mode: NarrationBalanceMode): ModeSpec? = when (mode) {
    NarrationBalanceMode.DEFAULT -> null
    NarrationBalanceMode.MOSTLY_DIALOGUE -> ModeSpec(
        "Mostly spoken",
        "Balance: mostly spoken. About two thirds of the reply is dialogue; keep narration to what is needed to stage it."
    )
    NarrationBalanceMode.BALANCED -> ModeSpec(
        "Even",
        "Balance: even. About half dialogue, half narration."
    )
    NarrationBalanceMode.MOSTLY_NARRATION -> ModeSpec(
        "Mostly narration",
        "Balance: mostly narration. About two thirds of the reply is description, action and interior detail; spend dialogue sparingly and make each spoken line earn its place."
    )
}

private fun specFor(mode: VoiceMode): ModeSpec? = when (mode) {
    VoiceMode.DEFAULT -> null
    VoiceMode.SECOND_PRESENT -> ModeSpec(
        "Second person, present",
        "Voice: second person, present tense. Address the player directly as \"you\", and write what is happening now — \"You step inside\", never \"You stepped inside\"."
    )
    VoiceMode.THIRD_PAST -> ModeSpec(
        "Third person, past",
        "Voice: third person, past tense. Name characters or use their pronouns, never \"you\", and narrate as something that already happened."
    )
    VoiceMode.THIRD_PRESENT -> ModeSpec(
        "Third person, present",
        "Voice: third person, present tense. Name characters or use their pronouns, never \"you\", and write what is happening now."
    )
    VoiceMode.FIRST_PAST -> ModeSpec(
        "First person, past",
        "Voice: first person, past tense. Narrate from inside the character as \"I\", recounting what happened."
    )
}

private fun specsOf(settings: StyleSettings): List<ModeSpec> = listOfNotNull(
    specFor(settings.storyPaceMode),
    specFor(settings.toneMode),
    specFor(settings.involvementMode),
    specFor(settings.narrativeRhythmMode),
    specFor(settings.universeMode),
    specFor(settings.intensityMode),
    specFor(settings.replyLength),
    specFor(settings.narrationBalance),
    specFor(settings.voiceMode)
)

/**
 * The default prose register — moved here from the system prompt on 2026-08-23.
 *
 * ## Why it moved
 *
 * These rules used to sit in `## Writing style` in the system prompt, always on, for every chat.
 * That made them the single largest cause of "all my stories sound the same": a comedy, a stage
 * play and a slow-burn romance were all told to write like the same novelist, and anything trying to
 * write differently — an experience mode, a purchased style pack — had to argue against instructions
 * the app had already stated as fact.
 *
 * Here, a style pack can **replace** the block outright and a mode directive above it simply wins,
 * because both sit closer to the generation point. Cost is unchanged: the same text used to be sent
 * every turn in the system prompt, it is now sent every turn in the last turn instead.
 *
 * What stayed behind in the system prompt is only what no register may override: stay in character,
 * no meta-commentary, never write the player's character, reply in the app's language.
 */
const val BASE_CRAFT = """Default prose register — follow this unless a directive above replaces it:
- Write like a novelist collaborating on a scene, never like an assistant answering a question.
- Prefer concrete sensory detail, precise gesture and expressive silence over naming an emotion outright.
- Let personality show through diction, pacing, interruption, restraint, humour, vulnerability or provocation — never by listing traits.
- Vary sentence length and structure to match the moment: short beats for tension, longer passages for charged or visually rich moments.
- Interleave *italicised action* with spoken lines where it helps the scene. Do not force it into every line.
- Avoid stock phrasing, interchangeable character voices and repeated body-language formulas.
- Prefer the most characterful available response over the safest one."""

/**
 * The optional craft requirements (Réglages → Mémoire et longueur), as hard constraints.
 *
 * Replaces a block that stacked adjectives ("more crafted, vivid and alive", "denser with voice,
 * atmosphere and intent"). A model cannot verify whether it has been "vivid"; it can verify that it
 * did not open on a recap, that it varied sentence length, and that it avoided a named phrase.
 */
const val ENHANCED_CRAFT_DIRECTIVES = """Craft requirements. These are hard constraints, not suggestions:
- Open on a concrete image, action or line of dialogue. Never open by restating or summarising what the user just did.
- Include at least one specific sensory detail that could only belong to this scene — not one that would fit any scene.
- Vary sentence length deliberately inside the reply: at least one sentence under 8 words, and at least one over 25.
- Never write these: "a shiver ran down", "little did they know", "the air was thick with", "a mix of X and Y", "couldn't help but", or any sentence that names an emotion instead of showing it.
- Write longer and more developed than a standard reply, then cut every sentence that adds no information, image or tension.
- End on a beat that shifts the situation — a decision, a revelation, a gesture that changes what happens next. Never end on a neutral question handed back to the user."""

/**
 * The style contract appended as the **last** turn before generation.
 *
 * Position is the whole point. An instruction at index 0 competes with 40-60 transcript messages
 * that demonstrate the opposite style, and loses: the transcript is few-shot evidence, the system
 * prompt is a single assertion. Restating the active contract immediately before the generation
 * point is what actually makes a mode change take effect — and it keeps working after the
 * style-pivot marker ([buildStylePivot]) has scrolled out of the raw window, which a marker alone
 * cannot do.
 *
 * **Never null since 2026-08-23.** It used to return null when nothing was selected — which was the
 * state of every freshly created chat, so the app's most influential prompt slot sat empty for
 * exactly the conversations that needed steering most. Now it always carries at least [BASE_CRAFT],
 * which no longer lives in the system prompt. The ranks it declares are finally real: the style pack
 * and the global custom style are rendered *here*, at rank 3, instead of being named in a precedence
 * clause while actually being injected far above in the system prompt.
 */
fun buildStyleContract(settings: StyleSettings): String {
    val specs = specsOf(settings)
    val custom = settings.customDirective.trim()
    val never = settings.neverWrite.trim()
    val pack = settings.stylePackPrompt.trim()
    val globalStyle = settings.globalCustomStyle.trim()

    return buildString {
        appendLine("## Active style contract — highest priority")
        appendLine(
            "Write your next reply according to this contract. The transcript above was written " +
                "under earlier settings and is NOT the reference for how to write now — do not copy " +
                "its length, rhythm or register."
        )
        if (custom.isNotEmpty() && (specs.isNotEmpty() || pack.isNotEmpty() || globalStyle.isNotEmpty())) {
            appendLine(
                "When instructions conflict, this order wins: (1) the user's explicit instruction " +
                    "for this conversation, (2) the experience-mode directives, (3) the writing " +
                    "style pack or global custom style below."
            )
        }
        if (custom.isNotEmpty()) {
            appendLine()
            appendLine("### The user's explicit instruction for this conversation — follow it above all else")
            appendLine(custom)
        }
        if (specs.isNotEmpty()) {
            appendLine()
            specs.forEach { appendLine("- ${it.directive}") }
        }
        // Placed after the presets, and phrased as an override, so no mode directive above can be
        // read as licence to ignore it.
        if (never.isNotEmpty()) {
            appendLine()
            appendLine("### Never write this, whatever any instruction above may suggest")
            appendLine(never)
        }
        appendLine()
        if (pack.isNotEmpty()) {
            appendLine("### Writing style pack — this replaces the default prose register entirely")
            appendLine(pack)
        } else {
            appendLine(BASE_CRAFT)
        }
        if (globalStyle.isNotEmpty()) {
            appendLine()
            appendLine("### The user's global writing style, applied to every conversation")
            appendLine(globalStyle)
        }
        if (settings.enhancedCraft) {
            appendLine()
            appendLine(ENHANCED_CRAFT_DIRECTIVES)
        }
    }.trimEnd()
}

/** One `Was -> Now` line per setting that actually changed. Empty when nothing changed. */
private fun changedLines(old: StyleSettings, new: StyleSettings): List<String> = buildList {
    fun compare(label: String, before: ModeSpec?, after: ModeSpec?) {
        if (before?.label == after?.label) return
        add("$label: ${before?.label ?: "none"} -> ${after?.label ?: "none"}")
    }
    if (old.enhancedCraft != new.enhancedCraft) {
        add("Craft requirements: ${if (old.enhancedCraft) "on" else "off"} -> ${if (new.enhancedCraft) "on" else "off"}")
    }
    compare("Story structure", specFor(old.storyPaceMode), specFor(new.storyPaceMode))
    compare("Tone", specFor(old.toneMode), specFor(new.toneMode))
    compare("Involvement", specFor(old.involvementMode), specFor(new.involvementMode))
    compare("Pacing", specFor(old.narrativeRhythmMode), specFor(new.narrativeRhythmMode))
    compare("Setting", specFor(old.universeMode), specFor(new.universeMode))
    compare("Maturity", specFor(old.intensityMode), specFor(new.intensityMode))
    compare("Reply length", specFor(old.replyLength), specFor(new.replyLength))
    compare("Balance", specFor(old.narrationBalance), specFor(new.narrationBalance))
    compare("Voice", specFor(old.voiceMode), specFor(new.voiceMode))
    // The pack's own prose is never echoed into the transcript — it can run to a dozen lines, and
    // the pivot only needs to say that the register changed, not restate it.
    if (old.stylePackPrompt.trim() != new.stylePackPrompt.trim()) {
        add(
            if (new.stylePackPrompt.isBlank()) "Writing style pack: removed — back to the default prose register."
            else "Writing style pack: changed. The register below replaces the default one entirely."
        )
    }
    if (old.customDirective.trim() != new.customDirective.trim()) {
        val now = new.customDirective.trim()
        add(if (now.isEmpty()) "The user's own instruction for this conversation was removed." else "The user's own instruction for this conversation is now: $now")
    }
}

/**
 * The directive injected into the transcript **at the exact point the settings changed**, so the
 * model sees a hard boundary between "how I was writing" and "how I must write now".
 *
 * The closing sentence is the load-bearing part: without it the model reads the new instruction
 * while still holding dozens of counter-examples above it, and keeps imitating them. Naming the
 * previous setting and explicitly demoting the earlier replies is what breaks that inertia.
 *
 * Returns null when nothing actually changed, so a dialog opened and closed without edits leaves
 * no trace in the conversation.
 */
fun buildStylePivot(old: StyleSettings, new: StyleSettings): String? {
    val changes = changedLines(old, new)
    if (changes.isEmpty()) return null

    return buildString {
        appendLine("[STYLE CHANGE — applies from this point onward]")
        changes.forEach { appendLine(it) }
        appendLine(
            "Everything you write after this point must follow the new settings. Do NOT imitate " +
                "the length, rhythm or register of your earlier replies above: they were written " +
                "under the previous settings and are no longer the reference."
        )
    }.trimEnd()
}
