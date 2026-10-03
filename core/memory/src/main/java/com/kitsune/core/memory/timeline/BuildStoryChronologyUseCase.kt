package com.kitsune.core.memory.timeline

import com.kitsune.core.data.local.entities.KeyMomentEntity
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.repository.KeyMomentRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import java.text.Normalizer
import javax.inject.Inject

/**
 * Builds the ordered event ledger injected into the system prompt as `## Story Chronology`
 * (FEATURES.md section 4).
 *
 * Both halves of the story's chronology already existed in the database but never reached the
 * model: [KeyMomentEntity] rows were only ever read by the visual timeline, the script doctor and
 * the novel export, and [LoreEntryEntity.occurredAt] was stored and hand-editable but never
 * injected and never sorted. This merges the two into one list the model can actually reason over.
 *
 * **Ordering.** `occurredAt` is free prose ("three days after the confrontation at the docks") and
 * cannot be sorted, by design — many fictional settings have no real calendar. So it isn't used as
 * the sort key at all: it's the human-readable *label*, and ordering comes from `anchorCreatedAt`,
 * the real timestamp of the batch each entry was extracted from. Rows written before migration
 * v27→v28 carry `anchorCreatedAt = 0`, tie, and fall back to [KeyMomentEntity.momentOrder], which
 * puts them first — correct, since they are by construction the oldest. No backfill is needed.
 *
 * Pure apart from two reads: no network call, no LLM call, nothing written back.
 */
class BuildStoryChronologyUseCase @Inject constructor(
    private val keyMomentRepository: KeyMomentRepository,
    private val loreEntryRepository: LoreEntryRepository
) {
    suspend operator fun invoke(chatId: String, maxEntries: Int): List<String> {
        val moments = keyMomentRepository.getByChat(chatId)
        val events = loreEntryRepository.getByChat(chatId)
            .filter { it.entryType == LoreEntryType.EVENT && it.occurredAt.isNotBlank() }
        return build(moments, events, maxEntries)
    }

    /** Split out from [invoke] so the ordering, dedup, capping and rendering rules are testable
     *  without mocking repositories. */
    fun build(
        moments: List<KeyMomentEntity>,
        events: List<LoreEntryEntity>,
        maxEntries: Int
    ): List<String> {
        if (maxEntries <= 0) return emptyList()

        val entries = (moments.map(::fromMoment) + events.map(::fromEvent))
            .sortedWith(compareBy({ it.anchor }, { it.tiebreak }))

        val deduped = dedupe(entries)
        if (deduped.isEmpty()) return emptyList()

        return render(cap(deduped, maxEntries))
    }

    /**
     * Drops an event lore entry that restates a key moment from the same batch — the two extraction
     * prompts run on the same messages and routinely both pick up the scene's turning point, which
     * would otherwise appear twice in a row in the ledger.
     *
     * Requires *both* the same anchor and a real text overlap: same-anchor alone would collapse two
     * genuinely distinct beats from one batch. The key moment wins because it carries a title and a
     * mood, and because its summary is written to be read on a timeline.
     */
    private fun dedupe(sorted: List<Entry>): List<Entry> {
        val kept = mutableListOf<Entry>()
        for (entry in sorted) {
            val duplicate = kept.any { existing ->
                existing.anchor == entry.anchor &&
                    existing.isMoment != entry.isMoment &&
                    jaccard(existing.words, entry.words) >= DEDUPE_SIMILARITY
            }
            if (duplicate && !entry.isMoment) continue
            kept.add(entry)
        }
        return kept
    }

    /**
     * Keeps the [HEAD_ENTRIES] oldest entries plus the most recent ones, rather than just the tail:
     * the inciting incident of a story stays load-bearing for continuity long after it scrolls out
     * of every other memory layer, and a pure tail would drop exactly that.
     */
    private fun cap(entries: List<Entry>, maxEntries: Int): List<CappedEntry> {
        if (entries.size <= maxEntries) return entries.map { CappedEntry.Item(it) }

        val head = entries.take(HEAD_ENTRIES.coerceAtMost(maxEntries))
        val tail = entries.takeLast(maxEntries - head.size)
        val omitted = entries.size - head.size - tail.size

        return buildList {
            head.forEach { add(CappedEntry.Item(it)) }
            if (omitted > 0) add(CappedEntry.Elision(omitted))
            tail.forEach { add(CappedEntry.Item(it)) }
        }
    }

    private fun render(entries: List<CappedEntry>): List<String> {
        var index = 0
        return entries.map { entry ->
            when (entry) {
                is CappedEntry.Elision ->
                    "… (${entry.count} older events omitted here; they may still surface under Relevant Memories)"
                is CappedEntry.Item -> {
                    index++
                    val label = entry.value.label
                    if (label.isBlank()) "$index. ${entry.value.title} — ${entry.value.summary}"
                    else "$index. [$label] ${entry.value.title} — ${entry.value.summary}"
                }
            }
        }
    }

    private fun fromMoment(moment: KeyMomentEntity) = Entry(
        anchor = moment.anchorCreatedAt,
        tiebreak = moment.momentOrder,
        label = moment.storyTimeLabel.trim(),
        title = moment.title.trim(),
        summary = moment.summary.trim(),
        isMoment = true
    )

    private fun fromEvent(event: LoreEntryEntity) = Entry(
        anchor = event.anchorCreatedAt,
        // Lore entries have no momentOrder; ordering them after same-anchor key moments is the
        // stable choice, and the dedupe pass above usually removes them anyway.
        tiebreak = Int.MAX_VALUE,
        label = event.occurredAt.trim(),
        title = event.name.trim(),
        summary = event.summary.trim().ifBlank { event.content.trim() },
        isMoment = false
    )

    private class Entry(
        val anchor: Long,
        val tiebreak: Int,
        val label: String,
        val title: String,
        val summary: String,
        val isMoment: Boolean
    ) {
        val words: Set<String> by lazy { tokenize("$title $summary") }
    }

    private sealed interface CappedEntry {
        class Item(val value: Entry) : CappedEntry
        class Elision(val count: Int) : CappedEntry
    }

    private companion object {
        /** Oldest entries always kept when the ledger is capped. */
        const val HEAD_ENTRIES = 2

        /** Word-overlap ratio above which a same-batch event and key moment are the same beat. */
        const val DEDUPE_SIMILARITY = 0.5f

        fun jaccard(a: Set<String>, b: Set<String>): Float {
            if (a.isEmpty() || b.isEmpty()) return 0f
            val intersection = a.count { it in b }
            val union = a.size + b.size - intersection
            return if (union == 0) 0f else intersection.toFloat() / union
        }

        /** Lowercased, accent-folded words of 3+ characters — so "révélation" and "revelation",
         *  or "Le Port" and "le port", count as the same word. */
        fun tokenize(text: String): Set<String> =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length >= 3 }
                .toSet()
    }
}
