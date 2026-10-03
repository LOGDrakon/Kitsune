package com.kitsune.core.memory.lore

import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.NpcEntity
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.core.data.repository.ChatParticipantRepository
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.PersonaRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/**
 * Keeps an ensemble/universe chat's cast in sync with the characters its own story actually talks
 * about (requested by the user: "un moyen pour l'IA de rajouter de nouvelles fiches de PNJ quand
 * ça devient nécessaire" + "réintroduire dans un chat d'ensemble un nouveau persona ou PNJ...
 * pour qu'il y ait tous les personnages de l'univers"). Runs off the same batch/trigger as
 * [ExtractLoreEntriesUseCase] (level 3 lore), right after it, in [UpdateChatSummaryUseCase] — every
 * `CHARACTER`-type lore entry the story has produced so far is resolved against this universe's
 * existing personas/NPCs:
 * - Already a cast member of this chat → nothing to do.
 * - Matches an existing universe persona/NPC by name, just not yet in this chat's cast → added
 *   back in (the character was already part of the universe, the story simply hadn't brought them
 *   up in this scene yet — this is what makes the re-addition feel retroactive rather than a sudden
 *   arrival, matching the user's explicit ask: "on ne parlait pas de x jusque là, maintenant si").
 * - Matches nobody at all → a genuinely new character invented by the story mid-scene: a fresh
 *   [NpcEntity] is created for them (never a [PersonaEntity][com.kitsune.core.data.local.entities.PersonaEntity]
 *   — the persona-age-verification invariant, FEATURES.md section 3, means only the
 *   manual persona-creation flow may ever decide an age, so autonomous background creation is
 *   restricted to NPCs, which carry no such lock and default to `age = null`).
 *
 * Silent by design — no confirmation, no visible narration — this only ever runs for ensemble
 * chats (`personaId == null && universeId != null`); a regular single-persona chat has no "cast"
 * concept to extend and today never even has `universeId` set (see `ChatRepositoryImpl.createChat`).
 */
class SyncCastFromLoreUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val loreEntryRepository: LoreEntryRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val npcRepository: NpcRepository,
    private val personaRepository: PersonaRepository
) {
    suspend operator fun invoke(chatId: String) {
        val chat = chatRepository.getById(chatId) ?: return
        val universeId = chat.universeId
        if (chat.personaId != null || universeId == null) return

        val characterEntries = loreEntryRepository.getByChat(chatId)
            .filter { it.entryType == LoreEntryType.CHARACTER }
        if (characterEntries.isEmpty()) return

        val currentParticipantIds = chatParticipantRepository.getByChat(chatId).map { it.participantId }.toSet()
        val universeNpcs = npcRepository.getByUniverse(universeId).first()
        val universePersonas = personaRepository.observeByUniverse(universeId).first()

        characterEntries.distinctBy { it.name.trim().lowercase() }.forEach { entry ->
            val normalizedName = entry.name.trim().lowercase()
            val matchingNpc = universeNpcs.find { it.name.trim().lowercase() == normalizedName }
            val matchingPersona = universePersonas.find { it.name.trim().lowercase() == normalizedName }

            when {
                matchingNpc != null -> {
                    bumpImportanceIfNeeded(matchingNpc, entry.version)
                    reAddIfMissing(chatId, ParticipantType.NPC, matchingNpc.id, currentParticipantIds)
                }
                matchingPersona != null -> reAddIfMissing(chatId, ParticipantType.PERSONA, matchingPersona.id, currentParticipantIds)
                else -> {
                    val npc = NpcEntity(
                        id = UUID.randomUUID().toString(),
                        universeId = universeId,
                        name = entry.name,
                        description = entry.content.ifBlank { entry.summary },
                        personality = "",
                        role = "Personnage secondaire",
                        factionId = null,
                        locationId = null,
                        age = null,
                        importance = entry.version,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    npcRepository.insert(npc)
                    chatParticipantRepository.addParticipant(chatId, ParticipantType.NPC, npc.id)
                }
            }
        }
    }

    /** Keeps [NpcEntity.importance] in step with how many times the lore pipeline has re-extracted
     * this character (see [com.kitsune.core.data.local.entities.LoreEntryEntity.version]) — never
     * decreases, since a character mentioned less in a later batch is not "less important" than
     * before. */
    private suspend fun bumpImportanceIfNeeded(npc: NpcEntity, loreVersion: Int) {
        if (loreVersion <= npc.importance) return
        npcRepository.update(npc.copy(importance = loreVersion, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun reAddIfMissing(
        chatId: String,
        type: ParticipantType,
        participantId: String,
        currentParticipantIds: Set<String>
    ) {
        if (participantId in currentParticipantIds) return
        chatParticipantRepository.addParticipant(chatId, type, participantId)
    }
}
