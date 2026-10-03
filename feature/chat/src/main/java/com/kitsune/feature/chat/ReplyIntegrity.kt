package com.kitsune.feature.chat

/**
 * Decides whether a generated reply is a story beat or garbage (2026-09-01).
 *
 * ## The incident this exists for
 *
 * A user reported "messages nuls" and attached a transcript in which the assistant had, over the
 * course of one evening, produced: the literal string `null`; several messages that were **stage
 * directions rather than prose** ("Durcis les enjeux. Incite au conflit", "Maintenant écris ta
 * réponse."); one message that quoted our own style contract back at the player ("RAPPEL : …"); and
 * finally multilingual token soup. All of it was written into the story, permanently, next to real
 * scenes.
 *
 * ## Why the model does this
 *
 * The last thing the model sees before generating is the style contract — a block of imperative
 * French, sent as a `system` turn *after* the player's message because that position is the one this
 * codebase proved actually beats transcript style inertia. That trade has a failure mode: a model
 * that loses the boundary continues the **instruction** register instead of switching to prose. It
 * became far more likely on 2026-08-25, when the default prose register, the style pack and the
 * global style all moved into that block and roughly doubled its length.
 *
 * A prompt cannot fully prevent this — the same instruction that says "write prose" is itself prose
 * the model can continue. So the reply is checked on the way out, where the evidence is unambiguous.
 *
 * ## Precision over recall
 *
 * A false positive deletes a reply the player wanted, so every rule here keys on something that
 * cannot occur in roleplay prose: an empty answer, our own instructions repeated verbatim, or
 * scaffolding vocabulary no character would ever say. A borderline-odd reply is kept.
 */
sealed interface ReplyVerdict {
    data object Usable : ReplyVerdict

    /** [reason] is for the log, never for the user — they get a plain retry. */
    data class Discard(val reason: String) : ReplyVerdict
}

/**
 * Scaffolding that only ever appears in instructions, never in a character's mouth.
 *
 * Every entry was taken from the reported transcript rather than imagined, and each is long enough
 * that prose cannot produce it by accident.
 */
private val INSTRUCTION_MARKERS = listOf(
    "maintenant écris ta réponse",
    "écris ta réponse",
    "does not apply",
    "## active style contract",
    "default prose register",
    "macro-structure:",
    "involvement:",
    "reply length:",
    "pro mode — craft requirements",
    "[style change",
    "ta réponse fait repartir",
    "toutes les règles ci-dessus",
    "les règles ci-dessus sont prioritaires"
)

/** Shortest instruction line worth treating as a fingerprint. Below this, overlap is coincidence. */
private const val MIN_ECHO_LINE = 40

/**
 * @param reply what the model produced.
 * @param instructionsSent the contract and beat directive sent on this turn, when there were any.
 *   Passing them lets the check catch echoes without maintaining a list of our own phrasings — the
 *   contract's wording changes often, and a hardcoded copy would rot.
 */
fun assessReply(reply: String, instructionsSent: String?): ReplyVerdict {
    val trimmed = reply.trim()

    if (trimmed.isEmpty()) return ReplyVerdict.Discard("empty")
    // Seen in the wild: the model answers with the literal word rather than with nothing, which
    // slipped past every blank check and landed in the story as a message reading "null".
    if (trimmed.equals("null", ignoreCase = true) || trimmed.equals("undefined", ignoreCase = true)) {
        return ReplyVerdict.Discard("literal $trimmed")
    }

    val lower = trimmed.lowercase()
    INSTRUCTION_MARKERS.firstOrNull { it in lower }?.let {
        return ReplyVerdict.Discard("instruction marker: $it")
    }

    // The strongest signal, and the one that needs no maintenance: the reply repeats a line we sent
    // it. A character can be told to be vulgar; they never recite the rule that told them so.
    instructionsSent
        ?.lineSequence()
        ?.map { it.trim().trimStart('-', '#', ' ') }
        ?.filter { it.length >= MIN_ECHO_LINE }
        ?.firstOrNull { it.lowercase() in lower }
        ?.let { return ReplyVerdict.Discard("echoed instruction: ${it.take(60)}") }

    return ReplyVerdict.Usable
}
