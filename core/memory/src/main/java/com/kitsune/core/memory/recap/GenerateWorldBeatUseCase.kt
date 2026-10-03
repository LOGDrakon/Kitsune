package com.kitsune.core.memory.recap

import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import javax.inject.Inject

/** Open threads offered to the beat. Few on purpose: it must pay one debt, not survey them all. */
private const val BEAT_MAX_THREADS = 3

/**
 * What happened in the story **while the player was away** (2026-08-25).
 *
 * ## Why this is the signature feature
 *
 * A recap tells you what *you* did. This tells you what happened *without you* — a character acted on
 * what they want, a dangling promise moved, someone arrived. It is the difference between a shelf of
 * archives and a library of living stories, and it is the one thing in this product a competitor
 * cannot ship next quarter: producing it requires the interiority fields, the aged unresolved threads
 * and the chronology that already exist here and nowhere else.
 *
 * ## Why it costs nothing
 *
 * It takes over the slot [GenerateRecapUseCase] already occupies — same trigger
 * (`shouldRecap`, on reopening a chat after a long pause), same `RECAP` operation type, same cheap
 * summary model, same single call. Nothing new is spent; a call that produced a summary now produces
 * an event.
 *
 * ## The three rules that keep it honest
 *
 * 1. **It never invents a plot.** The beat must move something the story already established — a
 *    character's stated desire, a specific open thread. A model free to invent produces events the
 *    next turn has never heard of, which destroys the continuity it was meant to feed.
 * 2. **It never asks for the player.** The market's retention hook is the character missing you
 *    ("I miss talking to you!"); that is a leash, not a story. Here the *plot* moved and the player
 *    is free to ignore it. [beatMentionsThePlayer] guards this and is tested.
 * 3. **It returns null rather than filler.** With no desire and no open thread there is nothing that
 *    could have happened, and the honest output is a plain recap instead — see [hasMaterial].
 */
class GenerateWorldBeatUseCase @Inject constructor(
    private val loreEntryRepository: LoreEntryRepository,
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver
) {

    /**
     * True when the story has something that could plausibly have moved on its own.
     *
     * Pure, so the caller can decide between a world beat and a plain recap without spending a call.
     */
    fun hasMaterial(persona: PersonaEntity?, openThreads: List<LoreEntryEntity>): Boolean {
        val hasDrive = persona != null &&
            (persona.desire.isNotBlank() || persona.fear.isNotBlank() || persona.moralLine.isNotBlank())
        return hasDrive || openThreads.isNotEmpty()
    }

    /** Unresolved narrative promises, oldest first — the ones the story owes the player. */
    suspend fun openThreads(chatId: String): List<LoreEntryEntity> =
        runCatching {
            loreEntryRepository.getByChat(chatId)
                .filter { it.entryType == LoreEntryType.THREAD && !it.resolved }
                .sortedBy { it.anchorCreatedAt }
                .take(BEAT_MAX_THREADS)
        }.getOrDefault(emptyList())

    /**
     * @param daysAway how long the story sat untouched, so the beat can be proportionate — an evening
     *   and three weeks should not produce the same amount of change.
     * @return the beat, or null when the model returned nothing usable or broke rule 2.
     */
    suspend fun generate(
        chatId: String,
        persona: PersonaEntity?,
        chatSummary: String,
        openThreads: List<LoreEntryEntity>,
        daysAway: Long
    ): String? {
        if (!hasMaterial(persona, openThreads)) return null

        val userPrompt = buildString {
            appendLine("Time the player has been away: ${daysAway.coerceAtLeast(1)} day(s).")
            appendLine()
            if (chatSummary.isNotBlank()) {
                appendLine("Where the story stands:")
                appendLine(chatSummary)
                appendLine()
            }
            persona?.let { p ->
                appendLine("The character who could have acted, and what drives them:")
                appendLine("Name: ${p.name}")
                if (p.desire.isNotBlank()) appendLine("Wants: ${p.desire}")
                if (p.fear.isNotBlank()) appendLine("Fears: ${p.fear}")
                if (p.flaw.isNotBlank()) appendLine("Self-sabotages by: ${p.flaw}")
                if (p.moralLine.isNotBlank()) appendLine("Will never: ${p.moralLine}")
                appendLine()
            }
            if (openThreads.isNotEmpty()) {
                appendLine("Promises this story has made and not yet kept:")
                openThreads.forEach { appendLine("- ${it.name}: ${it.summary}") }
            }
        }

        val result = chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.SUMMARY),
            systemPrompt = SYSTEM_PROMPT.trim(),
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = userPrompt)),
            operationType = "RECAP",
            maxTokens = 384
        )

        val beat = result.getOrNull()?.content?.trim()?.takeIf { it.isNotBlank() } ?: return null
        // Rule 2, enforced rather than merely requested: a model that slips into "she has been
        // waiting for you" turns the feature into the guilt-trip it was built to avoid.
        return beat.takeUnless { beatMentionsThePlayer(it) }
    }

    private companion object {
        private const val SYSTEM_PROMPT = """
You write what happened in an ongoing roleplay story WHILE THE PLAYER WAS AWAY.
This is not a recap. Nothing here summarises what the player already saw — you are reporting events
that occurred in their absence, off-screen.
Rules:
- Move something the story has ALREADY established: act on what the character wants or fears, or
  advance one of the unkept promises listed. Never invent a new plot, a new conflict or a new
  character that nothing above supports.
- Advance ONE thing. A single concrete event, not a montage.
- Scale it to how long the player was away: a day is a small step, three weeks is a real development.
- The character acted for their own reasons. They were not waiting, they did not miss the player, and
  they are not asking for anything. Never address the player, never mention their absence, never
  imply they owe the story attention.
- Stay strictly consistent with what is established: nothing that contradicts the summary or crosses
  the character's stated hard line.
- 2-3 sentences, past tense, concrete and specific. No preamble, no commentary, no title.
- Write in the same language as the story.
        """

        /**
         * Catches the failure mode rule 2 exists to prevent.
         *
         * Deliberately narrow: it looks for the character turning toward the player (waiting for
         * them, missing them, asking after them), not for any second-person pronoun — a story written
         * in second person would otherwise be unable to produce a beat at all.
         */
        private val PLAYER_DIRECTED = listOf(
            Regex("""\b(waiting|waited)\s+for\s+(you|your)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(miss|missed|misses)\s+(you|your)\b""", RegexOption.IGNORE_CASE),
            Regex("""\battend(ait|ait toujours|)\s+(ton|ta|votre|vous)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(tu lui manques|vous lui manquez|elle t'attend|il t'attend)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bwhere\s+(you|your)\s+(were|had gone)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(o[uù])\s+(tu|vous)\s+[ée]t(ais|iez)\b""", RegexOption.IGNORE_CASE)
        )

        fun beatMentionsThePlayer(text: String): Boolean = PLAYER_DIRECTED.any { it.containsMatchIn(text) }
    }

    /** Exposed for tests: the guard that keeps a story beat from becoming a retention hook. */
    internal fun isPlayerDirected(text: String): Boolean = beatMentionsThePlayer(text)
}
