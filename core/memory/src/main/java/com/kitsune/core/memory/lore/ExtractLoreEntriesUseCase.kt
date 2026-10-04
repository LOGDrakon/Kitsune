package com.kitsune.core.memory.lore

import com.kitsune.core.common.memory.MemorySettingsHolder
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.storyContentOnly
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.memory.semantic.IndexMemoryFragmentUseCase
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.json.AiJsonParser
import com.kitsune.core.network.json.AiJsonParser.stringField
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import javax.inject.Inject

private const val SYSTEM_PROMPT = """
You extract persistent story entities from a batch of roleplay messages, so they can be tracked
across a long story even once these exact messages scroll out of context. Extract every character
(including minor/secondary ones, not only protagonists), location, faction, notable event and
important item that is introduced, described or meaningfully developed in the messages below — but
only entries that are likely to matter again later; skip anything already fully covered by an
existing entity unless the new messages add real information. Prefer stable facts, distinctive
traits, and relationship-defining details over exhaustive completeness.
Also track the story's internal timeline. If the messages below establish or move forward the
in-fiction date, season, time of day, or elapsed time since a landmark event, produce a short,
self-contained, CUMULATIVE sentence describing the current point in the story's timeline (not a
delta) — e.g. "Early winter, three days after the confrontation at the docks, mid-morning." If
nothing about time is stated or implied by the new messages, return null for it. Never invent a
time skip that isn't in the text.
Respond with a single JSON object only, no markdown, of the exact shape:
{"entries": [{"name": "...", "entryType": "CHARACTER|LOCATION|FACTION|EVENT|ITEM|THREAD", "summary": "...", "content": "...", "occurredAt": "..." | null, "aliases": ["..."], "sameAs": "..." | null, "stance": "..." | null, "resolved": true | false}], "currentStoryTime": "..." | null}
"aliases": every OTHER name the messages use for this same entity — nicknames, titles, epithets,
role-descriptions used in place of the name ("la mercenaire", "the old man", "Captain"), alternate
spellings, and diminutives. Empty array if there are none. Never repeat the entity's own "name" here.
"sameAs": if this entry is in fact the SAME entity as one of the "Already known entities" listed
above, referred to under a different name, put that known entity's exact name here so the two are
merged instead of duplicated; otherwise null. Only do this when the text makes the identity
unambiguous — never merge two entities on a guess.
"summary": one concise sentence. "content": a short factual paragraph capturing every concrete
detail worth remembering (appearance, personality, relationships, role in the story, current
status). Never mention or imply an age for a character. "occurredAt": ONLY for entryType EVENT — a
short, self-contained description of when this event happened in the story's timeline (date,
season, time of day, or time elapsed relative to another known event), so a list of events can
later be sorted chronologically; null for every other entryType, and null for an EVENT too if the
text gives no time signal at all. "stance": ONLY for entryType CHARACTER — one short sentence on how this character currently regards
the player's character (trust, resentment, debt, attraction, wariness) and how it has shifted, based
on what the messages actually show; null if the messages give no signal. Never invent a feeling the
text does not support.
"THREAD": an open narrative promise the story has made and NOT yet kept — a question raised without
an answer, a threat announced, an unexplained object, an unpaid debt, a suspicion planted. This is
what lets the story pay things off later instead of forgetting them. Use the thread itself as
"name" (short, e.g. "La lettre non ouverte"), and "content" for what was planted and why it matters.
"resolved": ONLY for entryType THREAD — true when these messages actually pay the thread off
(the question is answered, the threat lands or is defused). Use "sameAs" to point at the existing
thread being resolved rather than creating a new entry. false otherwise, and false for every other
entryType. Do not mark a thread resolved just because it was mentioned again.
Return an empty "entries" array if nothing new or noteworthy is
introduced — this is a common and expected result, not a failure. Write every field in the same
language as the messages below.
"""

/** A stricter, more granular extraction pass — more EVENT entries for meaningful sub-beats, denser
 * "content" paragraphs. Added when the user picked a large memory
 * (`MemorySettingsHolder.detailedExtraction`): the roster then has room for the finer sheets. */
private const val DETAILED_EXTRACTION_ADDENDUM = """
This is a higher-fidelity extraction pass — be more thorough and more precise than usual:
- Lower the bar for EVENT entries: capture meaningful sub-beats and turning points within the scene
  (a revelation, a decision, a shift in a relationship, a small but consequential action), not only
  the single biggest event of the batch.
- Write denser, more specific "content" paragraphs: include concrete sensory and situational detail
  (exact wording of key promises or reveals, precise physical state, spatial/temporal specifics)
  rather than a generic summary — this content is reused later as authoritative continuity reference.
- Still skip anything trivial or already fully covered — precision and coverage matter more than
  quantity for its own sake.
- When reporting "currentStoryTime", be as precise as the text supports (exact time of day, precise
  elapsed duration) rather than a vague approximation.
"""

/**
 * Generates/updates the memory level-3 lore roster (FEATURES.md section 4: "fiches structurées
 * d'entités persistantes") for a batch of messages, matching the automatic generation/update
 * pipeline the spec calls for (previously schema-only). Deliberately triggered from the same batch
 * and cadence as [com.kitsune.core.memory.summarization.UpdateChatSummaryUseCase] (level 2) rather
 * than on every message — that's exactly the signal "this chunk of story is now behind us", the
 * right moment to extract lasting facts from it. Also covers the separate "auto-generate mini-fiches
 * for secondary NPCs" backlog item (FEATURES.md section 4): the prompt explicitly asks for minor
 * characters too, not just protagonists.
 *
 * Matches entries by name (case-insensitive, scoped to [chatId]) to update them in place — bumping
 * [LoreEntryEntity.version] — instead of creating duplicates every time the same character reappears.
 * Every extracted entry is also indexed for semantic retrieval ([IndexMemoryFragmentUseCase],
 * memory level 4).
 *
 * Never throws: on any failure (network, malformed AI response) this is a silent no-op, exactly
 * like an extraction pass that found nothing noteworthy — it must never block the caller's own
 * (level 2) summary update.
 */
class ExtractLoreEntriesUseCase @Inject constructor(
    private val loreEntryRepository: LoreEntryRepository,
    private val chatRepository: ChatRepository,
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver,
    private val indexMemoryFragmentUseCase: IndexMemoryFragmentUseCase
) {
    suspend operator fun invoke(chatId: String, messages: List<MessageEntity>) {
        if (messages.isEmpty()) return

        val chat = chatRepository.getById(chatId)
        val existing = loreEntryRepository.getByChat(chatId)
        val json = runCatching { extractEntries(chat, messages, existing) }.getOrNull() ?: return

        // Sortable counterpart to the free-text occurredAt: the batch's own end timestamp, which is
        // exactly what the caller writes to summarizedThroughCreatedAt. Lets BuildStoryChronologyUseCase
        // order events without having to parse in-fiction prose like "three days after the docks".
        val batchAnchor = messages.last().createdAt

        val entries = json["entries"]?.jsonArray.orEmpty()
        entries.forEach { element -> runCatching { upsertEntry(chatId, existing, element, batchAnchor) } }

        val newStoryTime = json.stringField("currentStoryTime").trim()
        if (newStoryTime.isNotBlank() && chat != null) {
            runCatching {
                chatRepository.upsert(chat.copy(storyTimeAnchor = newStoryTime, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    private suspend fun extractEntries(
        chat: ChatEntity?,
        messages: List<MessageEntity>,
        existing: List<LoreEntryEntity>
    ): JsonObject {
        val userPrompt = buildString {
            if (existing.isNotEmpty()) {
                appendLine("Already known entities:")
                existing.forEach { entry ->
                    // Feeding known aliases back in is what makes this self-reinforcing: once
                    // "la mercenaire" has been linked to Aria, the model sees the link on every
                    // later batch and stops proposing it as a new entity.
                    val aka = if (entry.aliases.isEmpty()) "" else " [aka: ${entry.aliases.joinToString(", ")}]"
                    appendLine("- ${entry.name}$aka (${entry.entryType}): ${entry.summary}")
                }
                appendLine()
            }
            if (!chat?.storyTimeAnchor.isNullOrBlank()) {
                appendLine("Current in-fiction date/time (if already known): ${chat?.storyTimeAnchor}")
                appendLine()
            }
            appendLine("New messages:")
            messages.storyContentOnly().forEach { appendLine("${speakerLabel(it)}: ${it.content}") }
        }

        val systemPrompt = if (MemorySettingsHolder.detailedExtraction) {
            SYSTEM_PROMPT.trim() + "\n" + DETAILED_EXTRACTION_ADDENDUM.trim()
        } else {
            SYSTEM_PROMPT.trim()
        }

        val result = chatCompletionRepository.complete(
                modelId = llmModelResolver.resolve(LlmOperation.LORE),
                systemPrompt = systemPrompt,
                messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = userPrompt)),
                operationType = "LORE",
                maxTokens = ChatCompletionRepository.MEMORY_MAX_TOKENS,
                allowContinuation = false
            ).getOrThrow()

        return AiJsonParser.parseObject(result.content, knownKeys = listOf("entries", "currentStoryTime"))
    }

    private suspend fun upsertEntry(
        chatId: String,
        existing: List<LoreEntryEntity>,
        element: JsonElement,
        batchAnchor: Long
    ) {
        val obj = element.jsonObject
        val name = obj.stringField("name").trim()
        val summary = obj.stringField("summary").trim()
        val content = obj.stringField("content").trim()
        if (name.isBlank() || (summary.isBlank() && content.isBlank())) return

        val entryType = runCatching { LoreEntryType.valueOf(obj.stringField("entryType").trim().uppercase()) }
            .getOrDefault(LoreEntryType.CHARACTER)
        val occurredAt = obj.stringField("occurredAt").trim()
        val aliases = (obj["aliases"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotBlank) }
            .filterNot { it.equals(name, ignoreCase = true) }
        val sameAs = obj.stringField("sameAs").trim()
        val stance = obj.stringField("stance").trim()
        val resolved = (obj["resolved"] as? JsonPrimitive)?.content?.trim()?.equals("true", ignoreCase = true) == true

        val now = System.currentTimeMillis()
        // Resolution order, cheapest and most certain first: the entry's own name, then a known
        // entity that already lists this name as an alias, then the model's explicit `sameAs` link,
        // then a database lookup across names and aliases.
        val match = existing.find { it.name.equals(name, ignoreCase = true) }
            ?: existing.find { known -> known.aliases.any { it.equals(name, ignoreCase = true) } }
            ?: sameAs.takeIf { it.isNotBlank() }
                ?.let { target -> existing.find { it.name.equals(target, ignoreCase = true) } }
            ?: loreEntryRepository.findByChatAndNameOrAlias(chatId, name)

        val entry = if (match != null) {
            // Neither occurredAt nor anchorCreatedAt is refreshed here — when an event happened is a
            // fixed fact once established, unlike summary/content which keep being enriched as the
            // story continues. Only a manual user edit (StoryMemoryViewModel.saveLoreEntry) changes
            // occurredAt; anchorCreatedAt is never edited at all. Refreshing either would make an
            // entity drift to "now" in the chronology every time it's merely mentioned again.
            //
            // A merge must never destroy anything: the union of aliases is kept, and the longer of
            // the two content paragraphs wins rather than the newer one. A `sameAs` link the model
            // got wrong should cost a redundant sheet, never a rewritten character.
            val mergedAliases = (match.aliases + aliases + name)
                .filterNot { it.equals(match.name, ignoreCase = true) }
                .distinctBy { it.lowercase() }
            match.copy(
                entryType = entryType,
                summary = summary.ifBlank { match.summary },
                content = if (content.length > match.content.length) content else match.content,
                aliases = mergedAliases,
                // La posture est la seule information qu'un merge REMPLACE au lieu d'enrichir : elle
                // décrit un état présent, pas un fait acquis. Garder l'ancienne parce qu'elle est
                // plus longue figerait la relation au premier jugement porté sur le personnage.
                stance = stance.ifBlank { match.stance },
                // Un fil ne se « dé-résout » pas : une fois payé il le reste, même si le modèle le
                // ré-extrait plus tard sans le drapeau.
                resolved = match.resolved || resolved,
                isAutoGenerated = true,
                version = match.version + 1,
                updatedAt = now
            )
        } else {
            LoreEntryEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                entryType = entryType,
                name = name,
                summary = summary,
                content = content,
                isAutoGenerated = true,
                occurredAt = occurredAt,
                anchorCreatedAt = batchAnchor,
                aliases = aliases.distinctBy { it.lowercase() },
                stance = stance,
                resolved = resolved,
                version = 1,
                createdAt = now,
                updatedAt = now
            )
        }

        loreEntryRepository.upsert(entry)
        indexMemoryFragmentUseCase(
            chatId = chatId,
            sourceType = MemoryFragmentSource.LORE_ENTRY,
            sourceKey = entry.id,
            text = "${entry.name}: ${entry.content.ifBlank { entry.summary }}"
        )
    }

    private fun speakerLabel(message: MessageEntity): String = when (message.role) {
        MessageRole.USER -> "User"
        MessageRole.ASSISTANT -> "Character"
        MessageRole.SYSTEM -> "System"
        // Filtered out upstream by storyContentOnly(); labelled rather than crashed on if one ever slips through.
        MessageRole.STYLE_DIRECTIVE -> "Style directive"
    }
}
