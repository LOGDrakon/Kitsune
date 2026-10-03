package com.kitsune.core.memory.summarization

import com.kitsune.core.common.memory.MemorySettingsHolder

/** Memory level 2-4 thresholds (FEATURES.md section 4: "résumés automatiques par segments").
 *  The raw window / lore roster sizes are configurable from the backend admin panel and stored in
 *  [MemorySettingsHolder]; defaults match the original hardcoded values. */
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

    /** How many lore entries get their full [content] paragraph injected, not just their one-line
     * summary — the best-ranked ones only, since `content` is several times longer. */
    const val DETAILED_LORE_ENTRIES = 4
    const val DETAILED_LORE_ENTRIES_PRO = 6

    /** Frozen chapters carried in the prompt. Older ones stay reachable through semantic retrieval
     *  (every closed chapter is indexed as a `CHAPTER_SUMMARY` fragment). */
    const val CHAPTERS_IN_PROMPT = 8

    /** Cap on the `## Story Chronology` ledger injected into the system prompt. */
    const val CHRONOLOGY_MAX_ENTRIES = 12
    const val CHRONOLOGY_MAX_ENTRIES_PRO = 18

    /** Messages held back from folding, i.e. the ones still sent verbatim to the model each turn.
     *
     * **This MUST stay equal to [rawWindowSize].** Before BUG-104 it was a separate hardcoded 30
     * while the prompt sent 40 (60 in Pro), so 10 messages (30 in Pro) were simultaneously present
     * verbatim in the raw window *and* already folded into the summary — paid for twice, in
     * context and in tokens.
     *
     * Note for anyone tempted to "just" swap the constant at the call site: the trigger threshold
     * has to move with it. With the old `size <= 40` gate, `dropLast(rawWindowSize)` yields a
     * 1-message batch in Standard and an **empty** batch in Pro — i.e. summarization, lore, cast
     * sync, indexing and key moments all silently dead, forever, for every Pro chat. See
     * `UpdateChatSummaryUseCase` for the gate that goes with this. */
    fun reservedWindow(isPro: Boolean = false): Int = rawWindowSize(isPro)

    fun detailedLoreEntries(isPro: Boolean = false): Int =
        if (isPro) DETAILED_LORE_ENTRIES_PRO else DETAILED_LORE_ENTRIES

    fun chronologyMaxEntries(isPro: Boolean = false): Int =
        if (isPro) CHRONOLOGY_MAX_ENTRIES_PRO else CHRONOLOGY_MAX_ENTRIES

    /** Direct, admin-configured message count sent verbatim to the AI each turn — a flat value,
     * not derived from the selected model's context window (demande explicite : "au lieu de
     * définir des seuils, on dit directement le nombre de messages bruts qu'on veut en contexte").
     * [isPro] picks the Pro-tier value ([MemorySettingsHolder.rawWindowSizePro]), itself a direct
     * admin-chosen number rather than a multiplier of the standard value — the panel computes and
     * displays the resulting ratio for reference, it isn't derived here. */
    fun rawWindowSize(isPro: Boolean = false): Int =
        if (isPro) MemorySettingsHolder.rawWindowSizePro else MemorySettingsHolder.rawWindowSize

    /** Same idea as [rawWindowSize] for the lore roster included in the prompt. */
    fun maxLoreEntries(isPro: Boolean = false): Int =
        if (isPro) MemorySettingsHolder.loreEntriesPro else MemorySettingsHolder.loreEntries
}
