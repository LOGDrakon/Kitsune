package com.kitsune.core.memory.lore

import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.memory.semantic.VectorMath
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow

/**
 * Picks which lore entries make it into the `## Known Entities` prompt section, by relevance to the
 * current turn rather than by pure recency.
 *
 * Before this, the roster was `getByChat(chatId).take(budget)` — i.e. `ORDER BY updatedAt DESC`, so
 * whichever entities happened to be touched by the last extraction pass, regardless of whether they
 * had anything to do with what the user just wrote.
 *
 * **Costs nothing extra.** Every lore entry is already indexed as a [MemoryFragmentSource.LORE_ENTRY]
 * fragment with a real embedding ([ExtractLoreEntriesUseCase] does it on every upsert), the fragment
 * list is already loaded for semantic retrieval, and the query embedding is already computed for the
 * same turn. The only new work is a few hundred cosine computations.
 *
 * Deliberately **not** merged into `RetrieveRelevantMemoryUseCase` despite the shared inputs: that
 * one returns fragment text for `## Relevant Memories` and must return *nothing* when the embedder
 * is down (no memories beats wrong memories), whereas this one returns entities for a different
 * section and must fall back to the previous recency roster (returning no entities at all would be
 * a hard regression). Folding them together would bend one of the two contracts. The shared I/O is
 * hoisted one level up instead, into `BuildTurnMemoryUseCase`.
 */
class RankLoreEntriesUseCase @Inject constructor() {

    /**
     * @param entries all lore entries for the chat, in `updatedAt DESC` order as [com.kitsune.core.data.repository.LoreEntryRepository.getByChat] returns them.
     * @param fragments every memory fragment for the chat; only [MemoryFragmentSource.LORE_ENTRY] rows are used here.
     * @param queryEmbedding the embedded current turn, or null if the embedder failed.
     * @param rawWindowText concatenated text of the messages being sent verbatim this turn.
     * @param budget maximum entries to return.
     */
    operator fun invoke(
        entries: List<LoreEntryEntity>,
        fragments: List<MemoryFragmentEntity>,
        queryEmbedding: List<Float>?,
        rawWindowText: String,
        budget: Int
    ): List<LoreEntryEntity> {
        if (budget <= 0 || entries.isEmpty()) return emptyList()
        // No embedding (offline, or the embedding endpoint failed) — degrade to exactly the
        // behaviour that shipped before ranking existed, rather than to an empty roster.
        if (queryEmbedding == null) return entries.take(budget)

        val embeddingByEntryId = fragments
            .filter { it.sourceType == MemoryFragmentSource.LORE_ENTRY }
            .associate { it.sourceKey to it.embedding }

        val scored = entries.mapIndexed { rank, entry ->
            val semantic = embeddingByEntryId[entry.id]
                ?.let { VectorMath.cosineSimilarity(queryEmbedding, it) }
                ?.coerceAtLeast(0f)
                ?: 0f
            val recency = 2f.pow(-rank / RECENCY_HALF_LIFE)
            val importance = min(entry.version, IMPORTANCE_CEILING) / IMPORTANCE_CEILING.toFloat()
            val typePrior = typePrior(entry.entryType)

            val score = SEMANTIC_WEIGHT * semantic +
                RECENCY_WEIGHT * recency +
                IMPORTANCE_WEIGHT * importance +
                TYPE_WEIGHT * typePrior

            entry to score
        }

        // Hard guarantee: an entity the model is about to read about in the raw window must never
        // be missing its sheet, however cold it looks to the scorer. Pinned entries are still
        // ordered among themselves by score, and never push the roster over budget.
        val pinnedIds = pinnedEntryIds(entries, rawWindowText)

        return scored
            .sortedWith(
                compareByDescending<Pair<LoreEntryEntity, Float>> { it.first.id in pinnedIds }
                    .thenByDescending { it.second }
            )
            .take(budget)
            .map { it.first }
    }

    private fun pinnedEntryIds(entries: List<LoreEntryEntity>, rawWindowText: String): Set<String> {
        if (rawWindowText.isBlank()) return emptySet()
        return entries.asSequence()
            // Aliases count as mentions: writing "la mercenaire" must pull up Aria's sheet just as
            // surely as writing "Aria" does.
            .filter { entry ->
                (listOf(entry.name) + entry.aliases)
                    .any { it.isNotBlank() && wholeWord(it).containsMatchIn(rawWindowText) }
            }
            .map { it.id }
            .toSet()
    }

    private companion object {
        const val SEMANTIC_WEIGHT = 0.55f
        const val RECENCY_WEIGHT = 0.20f
        const val IMPORTANCE_WEIGHT = 0.15f
        const val TYPE_WEIGHT = 0.10f

        /** Ranks (in the incoming `updatedAt DESC` list) after which the recency term halves.
         *  Rank-based rather than wall-clock, so a story played in one sitting — where every entry
         *  has almost the same `updatedAt` — still gets a usable gradient. */
        const val RECENCY_HALF_LIFE = 8f

        /** `version` counts how many times an entity has been re-extracted, which the codebase
         *  already treats as an importance signal (it seeds `NpcEntity.importance`). Capped so a
         *  long-running protagonist doesn't saturate the whole score. */
        const val IMPORTANCE_CEILING = 10

        fun typePrior(type: LoreEntryType): Float = when (type) {
            LoreEntryType.CHARACTER -> 1.0f
            LoreEntryType.LOCATION -> 0.7f
            LoreEntryType.FACTION -> 0.7f
            LoreEntryType.ITEM -> 0.6f
            // Events now have their own ordered `## Story Chronology` section, so they no longer
            // need to compete for room in the entity roster.
            LoreEntryType.EVENT -> 0.4f
            // Comme les événements : les fils ont leur propre section `## Fils en suspens`, classée
            // par ancienneté, et n'ont donc pas à disputer une place au roster d'entités. Le poids
            // bas les écarte d'ici sans les exclure d'une recherche sémantique qui les rencontrerait.
            LoreEntryType.THREAD -> 0.3f
        }

        /** Whole-word match using Unicode-letter lookaround rather than `\b`: Java's `\b` is defined
         *  over `[a-zA-Z0-9_]`, so a name starting or ending with an accented letter ("Élise") gets
         *  the boundary wrong. This also correctly refuses to match "Aria" inside "Ariane". */
        fun wholeWord(name: String): Regex =
            Regex("(?<!\\p{L})" + Regex.escape(name) + "(?!\\p{L})", RegexOption.IGNORE_CASE)
    }
}
