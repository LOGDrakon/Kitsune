package com.kitsune.core.memory.context

import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.StoryChapterEntity
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.data.repository.MemoryFragmentRepository
import com.kitsune.core.data.repository.StoryChapterRepository
import com.kitsune.core.memory.lore.RankLoreEntriesUseCase
import com.kitsune.core.memory.semantic.RemoteTextEmbedder
import com.kitsune.core.memory.semantic.RetrieveRelevantMemoryUseCase
import com.kitsune.core.memory.summarization.SummarizationConfig
import com.kitsune.core.memory.timeline.BuildStoryChronologyUseCase
import javax.inject.Inject

/** Assez pour que le modèle ait de quoi rappeler, assez peu pour ne pas transformer la section en
 *  liste de courses que le modèle survole. Les fils au-delà restent en base et remontent dès qu'un
 *  plus ancien est résolu. */
private const val MAX_OPEN_THREADS = 5

/** Everything the memory pipeline contributes to one turn's system prompt. */
data class TurnMemory(
    /** The **current** chapter's rolling summary; earlier chapters are in [chapters]. */
    val summary: String = "",
    /** Frozen chapters, oldest first, already capped to what the prompt should carry. */
    val chapters: List<StoryChapterEntity> = emptyList(),
    val storyTimeAnchor: String = "",
    val loreEntries: List<LoreEntryEntity> = emptyList(),
    val chronology: List<String> = emptyList(),
    val relevantMemories: List<String> = emptyList(),
    /**
     * Promesses narratives encore ouvertes, la plus ancienne d'abord (2026-08-22).
     *
     * Tenues à part de [loreEntries] : ce ne sont pas des entités du monde en compétition pour une
     * place au roster, mais une dette narrative dont l'**ancienneté** est l'information utile — d'où
     * le tri par `anchorCreatedAt` croissant plutôt que par pertinence.
     */
    val openThreads: List<OpenThread> = emptyList()
) {
    companion object {
        /** For prompt-building paths with no history to draw on yet (opening scene, arrival beat). */
        val EMPTY = TurnMemory()
    }
}

/**
 * Un fil narratif ouvert, accompagné de son **âge en tours**.
 *
 * L'âge est calculé ici plutôt que dans `ChatViewModel` pour la même raison que le reste de ce
 * fichier : ce module a des tests, l'assemblage du prompt n'en a pas. Et c'est l'information qui
 * porte tout l'intérêt du fil — un modèle ne perçoit aucune durée dans un transcript, un fil de
 * trente tours lui paraît aussi frais que celui du tour précédent.
 */
data class OpenThread(val entry: LoreEntryEntity, val ageTurns: Int)

/**
 * Single assembly point for the four memory layers, replacing four near-identical blocks that had
 * been copy-pasted through `ChatViewModel` (send, regenerate/edit, director beat, retry).
 *
 * Beyond removing the duplication, this exists so the logic is **testable**: `feature:chat` has no
 * `ChatViewModelTest` and no practical way to get one, whereas `core:memory` has real coverage.
 * Anything decided here — which entities are relevant, how the chronology is ordered, what counts
 * as already-visible — is decided in a module that can assert on it.
 *
 * It also makes the shared work explicit and paid for once. The four blocks each called
 * [RetrieveRelevantMemoryUseCase], which embedded the query itself; that single embedding now feeds
 * both semantic retrieval and lore ranking, and the fragment list is loaded once for both. Same
 * number of network calls as before, two consumers instead of one.
 */
class BuildTurnMemoryUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val loreEntryRepository: LoreEntryRepository,
    private val memoryFragmentRepository: MemoryFragmentRepository,
    private val storyChapterRepository: StoryChapterRepository,
    private val remoteTextEmbedder: RemoteTextEmbedder,
    private val rankLoreEntriesUseCase: RankLoreEntriesUseCase,
    private val buildStoryChronologyUseCase: BuildStoryChronologyUseCase,
    private val retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase
) {
    /**
     * @param query what the model is being asked to respond to — the user's message, or a director
     *   instruction.
     * @param rawWindow the messages being sent verbatim this turn, **descending** as
     *   [com.kitsune.core.data.repository.MessageRepository.getRecent] returns them.
     */
    suspend operator fun invoke(
        chatId: String,
        query: String,
        rawWindow: List<MessageEntity>
    ): TurnMemory {
        val chat = chatRepository.getById(chatId)
        // Lore now has three scopes, merged here rather than copied once (2026-08-25):
        //
        // - the **chat's** own sheets, extracted from this story as it went;
        // - the **character's**, which they carry into every story they appear in — this is where an
        //   imported card's `character_book` lands, and without this merge importing one would be
        //   decorative;
        // - the **universe's**, which used to be duplicated into an ensemble chat at creation time.
        //   Copying meant edits to the world never reached stories already under way, and a persona
        //   chat attached to a universe could not see it at all. Reading it live fixes both.
        //
        // De-duplicated by id because the copy-at-creation path still exists for older chats, so the
        // same sheet can legitimately arrive twice. Ranking downstream is unchanged.
        val entries = (
            loreEntryRepository.getByChat(chatId) +
                chat?.personaId?.let { runCatching { loreEntryRepository.getByPersona(it) }.getOrDefault(emptyList()) }.orEmpty() +
                chat?.universeId?.let { runCatching { loreEntryRepository.getByUniverse(it) }.getOrDefault(emptyList()) }.orEmpty()
            ).distinctBy { it.id }
        val fragments = memoryFragmentRepository.getByChat(chatId)

        // The last assistant message usually holds the referent of a short user reply ("what did
        // she mean by that?"), so embedding the pair recalls far better than the user text alone —
        // at no extra cost, since it is still one embedding call.
        val lastAssistant = rawWindow.firstOrNull { it.role == MessageRole.ASSISTANT }?.content.orEmpty()
        val retrievalQuery = buildString {
            append(query.trim())
            if (lastAssistant.isNotBlank()) {
                append('\n')
                append(lastAssistant.take(LAST_ASSISTANT_QUERY_CHARS))
            }
        }.take(MAX_QUERY_CHARS)

        val queryEmbedding = if (retrievalQuery.isBlank()) null
                             else remoteTextEmbedder.embed(retrievalQuery).getOrNull()

        val rankedLore = rankLoreEntriesUseCase(
            entries = entries,
            fragments = fragments,
            queryEmbedding = queryEmbedding,
            rawWindowText = rawWindow.joinToString("\n") { it.content },
            budget = SummarizationConfig.maxLoreEntries(chat)
        )

        // Les fils sont écartés du roster classé (poids de type bas dans RankLoreEntriesUseCase) et
        // repris ici dans leur propre liste : un fil compte par son âge, pas par sa pertinence
        // sémantique au tour courant — c'est justement celui qu'on a cessé de mentionner qu'il faut
        // faire ressurgir.
        val unresolved = entries
            .filter { it.entryType == LoreEntryType.THREAD && !it.resolved }
            .sortedBy { it.anchorCreatedAt }
            .take(MAX_OPEN_THREADS)

        // Une seule requête pour tous les fils : on charge les messages postérieurs au plus ancien,
        // puis chaque âge se déduit de cette même liste. Interroger par fil multiplierait la lecture
        // par cinq pour la même information.
        val openThreads = if (unresolved.isEmpty()) emptyList() else {
            val since = messageRepository.getAfter(chatId, unresolved.first().anchorCreatedAt)
            unresolved.map { thread ->
                OpenThread(thread, since.count { it.createdAt > thread.anchorCreatedAt })
            }
        }

        val relevantMemories = if (queryEmbedding == null) emptyList() else {
            retrieveRelevantMemoryUseCase.rank(
                fragments = fragments,
                queryEmbedding = queryEmbedding,
                // getRecent is descending, so the last element is the oldest message still visible
                // verbatim. Chunks at or after it would just echo the window back at the model.
                excludeChunksFromCreatedAt = rawWindow.lastOrNull()?.createdAt
            )
        }

        // Only the most recent chapters go in the prompt; anything older stays reachable through
        // semantic retrieval, since each closed chapter is indexed as a CHAPTER_SUMMARY fragment.
        val chapters = storyChapterRepository.getByChat(chatId).takeLast(SummarizationConfig.CHAPTERS_IN_PROMPT)

        return TurnMemory(
            summary = chat?.summary.orEmpty(),
            chapters = chapters,
            storyTimeAnchor = chat?.storyTimeAnchor.orEmpty(),
            loreEntries = rankedLore,
            chronology = buildStoryChronologyUseCase(chatId, SummarizationConfig.chronologyMaxEntries(chat)),
            relevantMemories = relevantMemories,
            openThreads = openThreads
        )
    }

    private companion object {
        const val LAST_ASSISTANT_QUERY_CHARS = 400
        const val MAX_QUERY_CHARS = 1000
    }
}
