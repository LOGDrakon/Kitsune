package com.kitsune.core.memory.context

import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.data.repository.MemoryFragmentRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.StoryChapterRepository
import com.kitsune.core.memory.lore.RankLoreEntriesUseCase
import com.kitsune.core.memory.semantic.RemoteTextEmbedder
import com.kitsune.core.memory.semantic.RetrieveRelevantMemoryUseCase
import com.kitsune.core.memory.timeline.BuildStoryChronologyUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Couvre la sélection des fils narratifs ouverts et le **calcul de leur âge**.
 *
 * C'est l'âge qui porte tout l'intérêt de la fonctionnalité : sans lui, la section `## Unresolved
 * threads` ne dit au modèle que « ces promesses existent », jamais « celle-ci traîne depuis trente
 * tours ». Le reste du prompt ne peut pas être testé (`feature:chat` n'a pas de test de
 * `ChatViewModel`), donc ce calcul est placé ici précisément pour l'être.
 */
class BuildTurnMemoryUseCaseTest {

    private val chatRepository = mockk<ChatRepository>(relaxed = true)
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val loreEntryRepository = mockk<LoreEntryRepository>(relaxed = true)
    private val memoryFragmentRepository = mockk<MemoryFragmentRepository>(relaxed = true)
    private val storyChapterRepository = mockk<StoryChapterRepository>(relaxed = true)
    private val remoteTextEmbedder = mockk<RemoteTextEmbedder>(relaxed = true)
    private val rankLoreEntriesUseCase = mockk<RankLoreEntriesUseCase>(relaxed = true)
    private val buildStoryChronologyUseCase = mockk<BuildStoryChronologyUseCase>(relaxed = true)
    private val retrieveRelevantMemoryUseCase = mockk<RetrieveRelevantMemoryUseCase>(relaxed = true)

    private val useCase = BuildTurnMemoryUseCase(
        chatRepository, messageRepository, loreEntryRepository, memoryFragmentRepository,
        storyChapterRepository, remoteTextEmbedder, rankLoreEntriesUseCase,
        buildStoryChronologyUseCase, retrieveRelevantMemoryUseCase
    )

    private fun thread(
        id: String,
        anchor: Long,
        resolved: Boolean = false,
        type: LoreEntryType = LoreEntryType.THREAD
    ) = LoreEntryEntity(
        id = id,
        chatId = CHAT,
        entryType = type,
        name = id,
        summary = "résumé de $id",
        content = "",
        anchorCreatedAt = anchor,
        resolved = resolved,
        createdAt = anchor,
        updatedAt = anchor
    )

    private fun message(createdAt: Long) = MessageEntity(
        id = "m$createdAt",
        chatId = CHAT,
        role = MessageRole.USER,
        content = "msg",
        imageAttachmentPath = null,
        tokenCount = 0,
        createdAt = createdAt
    )

    private suspend fun run(
        entries: List<LoreEntryEntity>,
        messages: List<MessageEntity>
    ): TurnMemory {
        // `Result` est une value class inline : un mock relaxed en fabrique un Object que
        // `getOrNull()` ne peut pas caster. On stube donc explicitement. L'échec est ici le cas le
        // plus représentatif — les fils ne dépendent pas du tout de l'embedding.
        coEvery { remoteTextEmbedder.embed(any()) } returns Result.failure(IllegalStateException("hors ligne"))
        coEvery { loreEntryRepository.getByChat(CHAT) } returns entries
        coEvery { messageRepository.getAfter(CHAT, any()) } answers {
            val after = secondArg<Long>()
            messages.filter { it.createdAt > after }
        }
        return useCase(CHAT, "query", rawWindow = emptyList())
    }

    @Test
    fun `un fil est daté par le nombre de messages postés depuis sa plantation`() = runTest {
        val memory = run(
            entries = listOf(thread("vieux", anchor = 100), thread("recent", anchor = 400)),
            messages = (1..5).map { message(it * 100L) } // 100, 200, 300, 400, 500
        )

        assertEquals(listOf("vieux", "recent"), memory.openThreads.map { it.entry.name })
        // Postérieurs à 100 : 200/300/400/500. Postérieurs à 400 : 500 seul.
        assertEquals(4, memory.openThreads[0].ageTurns)
        assertEquals(1, memory.openThreads[1].ageTurns)
    }

    @Test
    fun `le plus ancien vient en premier, quel que soit l ordre en base`() = runTest {
        val memory = run(
            entries = listOf(thread("c", 300), thread("a", 100), thread("b", 200)),
            messages = (1..4).map { message(it * 100L) }
        )
        assertEquals(listOf("a", "b", "c"), memory.openThreads.map { it.entry.name })
    }

    @Test
    fun `un fil résolu cesse d être injecté`() = runTest {
        val memory = run(
            entries = listOf(thread("payé", 100, resolved = true), thread("ouvert", 200)),
            messages = listOf(message(300))
        )
        assertEquals(listOf("ouvert"), memory.openThreads.map { it.entry.name })
    }

    @Test
    fun `seuls les THREAD sont des fils — un personnage ou un lieu n en est pas un`() = runTest {
        val memory = run(
            entries = listOf(
                thread("Aria", 100, type = LoreEntryType.CHARACTER),
                thread("la taverne", 100, type = LoreEntryType.LOCATION),
                thread("la dette", 100)
            ),
            messages = listOf(message(200))
        )
        assertEquals(listOf("la dette"), memory.openThreads.map { it.entry.name })
    }

    @Test
    fun `la liste est plafonnée, et ce sont les plus anciens qui restent`() = runTest {
        val memory = run(
            entries = (1..9).map { thread("f$it", anchor = it * 100L) },
            messages = listOf(message(1000))
        )
        assertEquals(5, memory.openThreads.size)
        assertEquals(listOf("f1", "f2", "f3", "f4", "f5"), memory.openThreads.map { it.entry.name })
    }

    @Test
    fun `sans fil ouvert, aucune lecture de messages n est faite`() = runTest {
        // Le cas de très loin le plus fréquent : la fonctionnalité ne doit rien coûter tant qu'aucun
        // fil n'existe.
        val memory = run(
            entries = listOf(thread("Aria", 100, type = LoreEntryType.CHARACTER)),
            messages = listOf(message(200))
        )
        assertTrue(memory.openThreads.isEmpty())
        coVerify(exactly = 0) { messageRepository.getAfter(any(), any()) }
    }

    @Test
    fun `tous les âges sortent d une seule lecture, pas d une par fil`() = runTest {
        // Cinq fils ne doivent pas produire cinq requêtes : l'ancrage le plus ancien borne déjà tous
        // les autres.
        run(
            entries = (1..5).map { thread("f$it", anchor = it * 100L) },
            messages = (1..6).map { message(it * 100L) }
        )
        coVerify(exactly = 1) { messageRepository.getAfter(CHAT, any()) }
    }

    @Test
    fun `un fil planté après le dernier message a un âge nul, pas négatif`() = runTest {
        val memory = run(
            entries = listOf(thread("tout frais", anchor = 999)),
            messages = listOf(message(100))
        )
        assertEquals(0, memory.openThreads.single().ageTurns)
    }

    private companion object {
        const val CHAT = "chat-1"
    }
}
