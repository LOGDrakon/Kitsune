package com.kitsune.feature.persona.card

import android.util.Log
import com.kitsune.core.data.card.CharacterBook
import com.kitsune.core.data.card.CharacterBookEntry
import com.kitsune.core.data.card.CharacterCard
import com.kitsune.core.data.card.CharacterCardEnvelope
import com.kitsune.core.data.card.CharacterCardParser
import com.kitsune.core.data.repository.EntrySceneRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.data.repository.ToneCardRepository
import com.kitsune.core.security.storage.EncryptedImageStore
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val TAG = "ExportCharacterCardUC"

/**
 * Writes a persona back out as a standard character card (2026-08-25).
 *
 * ## Why an app built on privacy also ships the exit
 *
 * "Your stories are yours" is a claim every competitor makes and none of them lets you test. A card
 * that opens in Chub, SillyTavern or RisuAI makes it checkable: the character leaves in a format
 * nobody controls, including us. That it also makes leaving easier is the point — a promise that only
 * holds while you cannot verify it is not a promise.
 *
 * Writes **both** the `chara` (V2) and `ccv3` (V3) chunks, the way Chub does, so a reader that knows
 * only one of the two still finds what it expects.
 */
class ExportCharacterCardUseCase @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val entrySceneRepository: EntrySceneRepository,
    private val loreEntryRepository: LoreEntryRepository,
    private val toneCardRepository: ToneCardRepository,
    private val encryptedImageStore: EncryptedImageStore
) {
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    /**
     * @return the PNG bytes of the card, or null when the persona has no avatar to carry it.
     *
     * A card *is* an image with metadata attached; there is no such thing as a card without a
     * picture. Rather than invent a blank one, the caller is told so it can ask the user for an
     * avatar first.
     */
    suspend operator fun invoke(personaId: String): ByteArray? {
        val persona = personaRepository.getById(personaId) ?: return null
        val avatarBytes = persona.avatarImageId?.let { encryptedImageStore.load(it) } ?: run {
            Log.i(TAG, "persona $personaId has no avatar; a card cannot be written without one")
            return null
        }

        val scenes = runCatching { entrySceneRepository.getByPersona(personaId) }.getOrDefault(emptyList())
        val lore = runCatching { loreEntryRepository.getByPersona(personaId) }.getOrDefault(emptyList())
        val tones = runCatching { toneCardRepository.getByPersona(personaId) }.getOrDefault(emptyList())

        val card = CharacterCard(
            name = persona.name,
            description = persona.shortDescription,
            personality = persona.personality,
            scenario = persona.scenario,
            firstMessage = persona.firstMessage,
            exampleDialogues = persona.exampleDialogues,
            // The reverse of the import: a tone card is this character's authored register, which is
            // what `system_prompt` means to every other reader.
            systemPrompt = tones.firstOrNull()?.directive.orEmpty(),
            alternateGreetings = scenes.map { it.firstMessage }.filter { it.isNotBlank() },
            characterBook = lore.takeIf { it.isNotEmpty() }?.let { entries ->
                CharacterBook(
                    name = "${persona.name} — codex",
                    entries = entries.map {
                        CharacterBookEntry(
                            // Aliases came from `keys` on the way in; they go back out the same way,
                            // with the entry's name as a fallback so a reader that relies on keyword
                            // activation still has something to match on.
                            keys = it.aliases.ifEmpty { listOf(it.name) },
                            content = it.content.ifBlank { it.summary },
                            name = it.name
                        )
                    }
                )
            },
            tags = persona.tags,
            characterVersion = persona.version.toString()
        )

        val payload = json.encodeToString(
            CharacterCardEnvelope.serializer(),
            CharacterCardEnvelope(spec = "chara_card_v2", specVersion = "2.0", data = card)
        )
        return CharacterCardParser.embed(avatarBytes, payload).also {
            if (it == null) Log.w(TAG, "avatar of $personaId is not a PNG; card could not be written")
        }
    }
}
