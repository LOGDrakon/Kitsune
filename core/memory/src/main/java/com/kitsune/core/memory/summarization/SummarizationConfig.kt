package com.kitsune.core.memory.summarization

import com.kitsune.core.common.memory.MemorySettingsHolder

/** Memory level 2-4 thresholds (FEATURES.md section 4: "résumés automatiques par segments").
 *  The raw window and lore roster sizes are the user's choice (Settings → Mémoire et longueur),
 *  held in [MemorySettingsHolder]; the sections that scale with them are derived here. */
object SummarizationConfig {
    /** Messages a batch must reach before it gets folded into the summary, once the story is
     * already under way. Deliberately much larger than the old backlog threshold (40 *total*
     * unsummarized, which produced ~11-message batches): a bigger batch means each fact is folded
     * in **once**, straight from the raw text, instead of being re-paraphrased by a dozen
     * successive fold-ins — fewer, larger steps are both cheaper and less lossy. */
    const val SUMMARIZE_BATCH_MIN = 40

    /** Lower bar for the very first fold of a chat, so a young story gets a summary, lore entries
     * and a timeline without waiting for [rawWindowSize] + [SUMMARIZE_BATCH_MIN] messages. */
    const val FIRST_BATCH_MIN = 12

    const val META_SUMMARY_TRIGGER_CHARS = 5000
    const val CHUNK_INTERVAL = 8

    /** Frozen chapters carried in the prompt. Older ones stay reachable through semantic retrieval
     *  (every closed chapter is indexed as a `CHAPTER_SUMMARY` fragment). */
    const val CHAPTERS_IN_PROMPT = 8

    /** Messages held back from folding, i.e. the ones still sent verbatim to the model each turn.
     *
     * **This MUST stay equal to [rawWindowSize].** Before BUG-104 it was a separate hardcoded 30
     * while the prompt sent 40, so 10 messages were simultaneously present verbatim in the raw
     * window *and* already folded into the summary — paid for twice, in context and in tokens.
     *
     * Note for anyone tempted to "just" swap the constant at the call site: the trigger threshold
     * has to move with it. With a fixed `size <= 40` gate, `dropLast(rawWindowSize)` yields a tiny
     * or **empty** batch as soon as the window grows — i.e. summarization, lore, cast sync, indexing
     * and key moments all silently dead for every chat with a large window. See
     * `UpdateChatSummaryUseCase` for the gate that goes with this. */
    fun reservedWindow(): Int = rawWindowSize()

    /** How many lore entries get their full `content` paragraph injected, not just their one-line
     * summary — the best-ranked ones only, since `content` is several times longer. 4 at the
     * default roster of 12. */
    fun detailedLoreEntries(): Int = (maxLoreEntries() / 3).coerceIn(2, 8)

    /** Cap on the `## Story Chronology` ledger injected into the system prompt; grows with the
     * roster, 12 at the default. */
    fun chronologyMaxEntries(): Int = maxLoreEntries().coerceIn(8, 24)

    /** Messages sent verbatim to the AI each turn — a flat number the user picks, not derived from
     * the model's context window (demande explicite : "on dit directement le nombre de messages
     * bruts qu'on veut en contexte"). */
    fun rawWindowSize(): Int = MemorySettingsHolder.rawWindowSize

    /** Same idea as [rawWindowSize] for the lore roster included in the prompt. */
    fun maxLoreEntries(): Int = MemorySettingsHolder.loreEntries
}
