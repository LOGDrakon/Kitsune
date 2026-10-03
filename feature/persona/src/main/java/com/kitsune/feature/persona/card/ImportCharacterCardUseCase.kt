package com.kitsune.feature.persona.card

import android.util.Log
import com.kitsune.core.data.card.CharacterCard
import com.kitsune.core.data.card.CharacterCardParser
import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.EntrySceneEntity
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.ToneCardEntity
import com.kitsune.core.data.repository.EntrySceneRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.data.repository.ToneCardRepository
import com.kitsune.core.security.storage.EncryptedImageStore
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ImportCharacterCardUC"

/** Cap on imported lore so a card with a thousand-entry book cannot flood the story memory. */
private const val MAX_IMPORTED_LORE = 100

/**
 * Brings a character card from Chub, SillyTavern, Agnai or RisuAI into Kitsune (2026-08-25).
 *
 * ## Why this matters more than it looks
 *
 * A new user opens Kitsune to an empty library and has to spend Ofudas before anything exists. With
 * this, they open a file they already own and arrive with the characters they have been playing for
 * months — free, offline, and without a byte leaving the device, which is the same promise the rest
 * of the app makes rather than an exception to it.
 *
 * ## The mapping is the interesting part
 *
 * Most apps that read cards discard `alternate_greetings` and `character_book` because they have
 * nowhere to put them. Kitsune has both already, with their own screens:
 *
 * - `alternate_greetings` → [EntrySceneEntity], the alternative openings
 * - `character_book.entries` → [LoreEntryEntity], the lore sheets
 * - `system_prompt` + `post_history_instructions` → a [ToneCardEntity], because "how this character
 *   should be written" is exactly what a tone card is
 * - the PNG itself → the avatar
 *
 * ## What it deliberately does not do
 *
 * No moderation filter (product decision, 2026-08-25): the file is local and private, and the server
 * moderation applies from the first message sent anyway. The **age** step is not a filter and does
 * stay — `PersonaEntity.age` is required, the card spec carries no age, and this app's rule is that
 * age is always entered by a human, never inferred. So the import produces a draft and hands it to
 * the normal review screen.
 */
class ImportCharacterCardUseCase @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val entrySceneRepository: EntrySceneRepository,
    private val loreEntryRepository: LoreEntryRepository,
    private val toneCardRepository: ToneCardRepository,
    private val encryptedImageStore: EncryptedImageStore,
    private val databaseProvider: KitsuneDatabaseProvider
) {

    /** Reads a card without writing anything, so the caller can show it before committing. */
    fun peek(pngBytes: ByteArray): CharacterCard? = CharacterCardParser.parse(pngBytes)

    /**
     * Writes the card into the library.
     *
     * @param age the 18+ value the user confirmed on the review screen — never taken from the card,
     *   which has no such field.
     * @return the new persona's id, or null when the file carried no readable card.
     */
    suspend operator fun invoke(
        pngBytes: ByteArray,
        age: Int,
        maturityTags: List<com.kitsune.core.data.local.entities.MaturityTag>
    ): String? {
        val card = CharacterCardParser.parse(pngBytes) ?: run {
            Log.i(TAG, "no character card chunk in this file (${pngBytes.size} bytes)")
            return null
        }

        val personaId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // The card's own image is the avatar. Failing to store it costs a portrait, never the import.
        val avatarImageId = runCatching { encryptedImageStore.save(pngBytes) }.getOrElse { e ->
            Log.w(TAG, "avatar could not be stored: ${e::class.simpleName}")
            null
        }

        val persona = PersonaEntity(
            id = personaId,
            universeId = null,
            name = card.name.ifBlank { "Sans nom" },
            shortDescription = card.description,
            personality = card.personality,
            scenario = card.scenario,
            firstMessage = card.firstMessage,
            exampleDialogues = card.exampleDialogues,
            age = age,
            maturityTags = maturityTags,
            tags = card.tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct(),
            visualSheetJson = null,
            avatarImageId = avatarImageId,
            createdAt = now,
            updatedAt = now
        )

        val scenes = card.alternateGreetings
            .filter { it.isNotBlank() }
            .mapIndexed { index, greeting ->
                EntrySceneEntity(
                    id = UUID.randomUUID().toString(),
                    personaId = personaId,
                    // Cards do not name their alternate greetings, so they are numbered rather than
                    // left blank — an unnamed chip in a picker is unusable.
                    title = "Ouverture ${index + 2}",
                    scenario = "",
                    firstMessage = greeting,
                    createdAt = now + index
                )
            }

        // Scoped to the character, not to a chat: a card's book is knowledge this person carries into
        // every story they appear in, and at import time no story exists yet. Capped so a
        // thousand-entry book cannot drown the memory pipeline.
        val lore = card.characterBook?.entries.orEmpty()
            .filter { it.enabled && it.content.isNotBlank() }
            .take(MAX_IMPORTED_LORE)
            .map { entry ->
                LoreEntryEntity(
                    id = UUID.randomUUID().toString(),
                    personaId = personaId,
                    // A card's book is undifferentiated world knowledge; ITEM is the most neutral
                    // type and the one with the lowest ranking prior, so imported sheets never crowd
                    // out the roster of entities the story itself established.
                    entryType = LoreEntryType.ITEM,
                    name = entry.name.ifBlank { entry.keys.firstOrNull().orEmpty().ifBlank { "Entrée" } },
                    summary = entry.content.take(300),
                    content = entry.content,
                    // Keyword activation has no equivalent here — Kitsune retrieves lore semantically
                    // and by name — so the keys become aliases, which keeps them doing useful work.
                    aliases = entry.keys.filter { it.isNotBlank() },
                    isAutoGenerated = false,
                    createdAt = now,
                    updatedAt = now
                )
            }

        val directive = listOf(card.systemPrompt, card.postHistoryInstructions)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val toneCard = directive.takeIf { it.isNotBlank() }?.let {
            ToneCardEntity(
                id = UUID.randomUUID().toString(),
                personaId = personaId,
                name = "Style de l'auteur",
                description = "Importé avec la carte de ${persona.name}.",
                directive = it,
                createdAt = now
            )
        }

        // One transaction: a half-imported character with its lore but no persona row, or scenes
        // pointing at nothing, would be worse than a clean failure.
        databaseProvider.runInTransaction {
            personaRepository.upsert(persona)
            scenes.forEach { entrySceneRepository.upsert(it) }
            lore.forEach { loreEntryRepository.upsert(it) }
            toneCard?.let { toneCardRepository.upsert(it) }
        }

        Log.d(TAG, "imported ${persona.name}: ${scenes.size} scene(s), ${lore.size} lore entr(ies)")
        return personaId
    }
}
