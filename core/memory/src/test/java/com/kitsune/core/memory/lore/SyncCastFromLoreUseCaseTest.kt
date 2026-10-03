package com.kitsune.core.memory.lore

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import com.kitsune.core.data.local.entities.ChatParticipantEntity
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.NpcEntity
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.repository.ChatParticipantRepository
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.PersonaRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val CHAT_ID = "chat-1"
private const val UNIVERSE_ID = "universe-1"

private fun ensembleChat() = ChatEntity(
    id = CHAT_ID,
    universeId = UNIVERSE_ID,
    personaId = null,
    title = "Scene",
    mode = ChatMode.CHAT,
    createdAt = 0L,
    updatedAt = 0L
)

private fun regularChat() = ChatEntity(
    id = CHAT_ID,
    universeId = null,
    personaId = "persona-1",
    title = "Chat",
    mode = ChatMode.CHAT,
    createdAt = 0L,
    updatedAt = 0L
)

private fun characterEntry(name: String, content: String = "A mysterious figure.", version: Int = 1) = LoreEntryEntity(
    id = "lore-$name",
    chatId = CHAT_ID,
    entryType = LoreEntryType.CHARACTER,
    name = name,
    summary = "$name appears.",
    content = content,
    version = version,
    createdAt = 0L,
    updatedAt = 0L
)

private fun npc(id: String, name: String, importance: Int = 1) = NpcEntity(
    id = id,
    universeId = UNIVERSE_ID,
    name = name,
    description = "",
    personality = "",
    role = "",
    factionId = null,
    locationId = null,
    age = null,
    importance = importance,
    createdAt = 0L,
    updatedAt = 0L
)

private fun persona(id: String, name: String) = PersonaEntity(
    id = id,
    universeId = UNIVERSE_ID,
    name = name,
    shortDescription = "",
    personality = "",
    scenario = "",
    firstMessage = "",
    exampleDialogues = "",
    age = 25,
    maturityTags = listOf(MaturityTag.SFW),
    visualSheetJson = null,
    createdAt = 0L,
    updatedAt = 0L
)

class SyncCastFromLoreUseCaseTest {

    private val chatRepository = mockk<ChatRepository>()
    private val loreEntryRepository = mockk<LoreEntryRepository>()
    private val chatParticipantRepository = mockk<ChatParticipantRepository>()
    private val npcRepository = mockk<NpcRepository>()
    private val personaRepository = mockk<PersonaRepository>()

    private val useCase = SyncCastFromLoreUseCase(
        chatRepository,
        loreEntryRepository,
        chatParticipantRepository,
        npcRepository,
        personaRepository
    )

    @Test
    fun `does nothing for a regular single-persona chat`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns regularChat()

        useCase(CHAT_ID)

        coVerify(exactly = 0) { loreEntryRepository.getByChat(any()) }
    }

    @Test
    fun `creates a new NPC and adds it to the cast for a genuinely new character`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns ensembleChat()
        coEvery { loreEntryRepository.getByChat(CHAT_ID) } returns listOf(characterEntry("Elara"))
        coEvery { chatParticipantRepository.getByChat(CHAT_ID) } returns emptyList()
        every { npcRepository.getByUniverse(UNIVERSE_ID) } returns flowOf(emptyList())
        every { personaRepository.observeByUniverse(UNIVERSE_ID) } returns flowOf(emptyList())
        coJustRun { npcRepository.insert(any()) }
        coJustRun { chatParticipantRepository.addParticipant(any(), any(), any()) }

        useCase(CHAT_ID)

        coVerify(exactly = 1) { npcRepository.insert(withArg { assertNpcName(it, "Elara") }) }
        coVerify(exactly = 1) { chatParticipantRepository.addParticipant(CHAT_ID, ParticipantType.NPC, any()) }
    }

    @Test
    fun `seeds a newly-created NPC's importance from the lore entry's version`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns ensembleChat()
        coEvery { loreEntryRepository.getByChat(CHAT_ID) } returns listOf(characterEntry("Elara", version = 4))
        coEvery { chatParticipantRepository.getByChat(CHAT_ID) } returns emptyList()
        every { npcRepository.getByUniverse(UNIVERSE_ID) } returns flowOf(emptyList())
        every { personaRepository.observeByUniverse(UNIVERSE_ID) } returns flowOf(emptyList())
        coJustRun { npcRepository.insert(any()) }
        coJustRun { chatParticipantRepository.addParticipant(any(), any(), any()) }

        useCase(CHAT_ID)

        coVerify(exactly = 1) { npcRepository.insert(withArg { org.junit.Assert.assertEquals(4, it.importance) }) }
    }

    @Test
    fun `bumps an existing NPC's importance when the lore entry's version has grown`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns ensembleChat()
        coEvery { loreEntryRepository.getByChat(CHAT_ID) } returns listOf(characterEntry("Elara", version = 5))
        coEvery { chatParticipantRepository.getByChat(CHAT_ID) } returns listOf(
            ChatParticipantEntity(id = "cp-1", chatId = CHAT_ID, participantType = ParticipantType.NPC, participantId = "npc-1", createdAt = 0L)
        )
        every { npcRepository.getByUniverse(UNIVERSE_ID) } returns flowOf(listOf(npc("npc-1", "Elara", importance = 2)))
        every { personaRepository.observeByUniverse(UNIVERSE_ID) } returns flowOf(emptyList())
        coJustRun { npcRepository.update(any()) }

        useCase(CHAT_ID)

        coVerify(exactly = 1) { npcRepository.update(withArg { org.junit.Assert.assertEquals(5, it.importance) }) }
    }

    @Test
    fun `never decreases an existing NPC's importance`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns ensembleChat()
        coEvery { loreEntryRepository.getByChat(CHAT_ID) } returns listOf(characterEntry("Elara", version = 1))
        coEvery { chatParticipantRepository.getByChat(CHAT_ID) } returns listOf(
            ChatParticipantEntity(id = "cp-1", chatId = CHAT_ID, participantType = ParticipantType.NPC, participantId = "npc-1", createdAt = 0L)
        )
        every { npcRepository.getByUniverse(UNIVERSE_ID) } returns flowOf(listOf(npc("npc-1", "Elara", importance = 7)))
        every { personaRepository.observeByUniverse(UNIVERSE_ID) } returns flowOf(emptyList())

        useCase(CHAT_ID)

        coVerify(exactly = 0) { npcRepository.update(any()) }
    }

    @Test
    fun `re-adds an existing universe NPC missing from this chat's cast without creating a duplicate`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns ensembleChat()
        coEvery { loreEntryRepository.getByChat(CHAT_ID) } returns listOf(characterEntry("Elara"))
        coEvery { chatParticipantRepository.getByChat(CHAT_ID) } returns emptyList()
        every { npcRepository.getByUniverse(UNIVERSE_ID) } returns flowOf(listOf(npc("npc-1", "Elara")))
        every { personaRepository.observeByUniverse(UNIVERSE_ID) } returns flowOf(emptyList())
        coJustRun { chatParticipantRepository.addParticipant(any(), any(), any()) }

        useCase(CHAT_ID)

        coVerify(exactly = 0) { npcRepository.insert(any()) }
        coVerify(exactly = 1) { chatParticipantRepository.addParticipant(CHAT_ID, ParticipantType.NPC, "npc-1") }
    }

    @Test
    fun `re-adds an existing universe persona missing from this chat's cast`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns ensembleChat()
        coEvery { loreEntryRepository.getByChat(CHAT_ID) } returns listOf(characterEntry("Kira"))
        coEvery { chatParticipantRepository.getByChat(CHAT_ID) } returns emptyList()
        every { npcRepository.getByUniverse(UNIVERSE_ID) } returns flowOf(emptyList())
        every { personaRepository.observeByUniverse(UNIVERSE_ID) } returns flowOf(listOf(persona("persona-1", "Kira")))
        coJustRun { chatParticipantRepository.addParticipant(any(), any(), any()) }

        useCase(CHAT_ID)

        coVerify(exactly = 0) { npcRepository.insert(any()) }
        coVerify(exactly = 1) { chatParticipantRepository.addParticipant(CHAT_ID, ParticipantType.PERSONA, "persona-1") }
    }

    @Test
    fun `does not re-add a character who is already part of this chat's cast`() = runTest {
        coEvery { chatRepository.getById(CHAT_ID) } returns ensembleChat()
        coEvery { loreEntryRepository.getByChat(CHAT_ID) } returns listOf(characterEntry("Elara"))
        coEvery { chatParticipantRepository.getByChat(CHAT_ID) } returns listOf(
            ChatParticipantEntity(id = "cp-1", chatId = CHAT_ID, participantType = ParticipantType.NPC, participantId = "npc-1", createdAt = 0L)
        )
        every { npcRepository.getByUniverse(UNIVERSE_ID) } returns flowOf(listOf(npc("npc-1", "Elara")))
        every { personaRepository.observeByUniverse(UNIVERSE_ID) } returns flowOf(emptyList())

        useCase(CHAT_ID)

        coVerify(exactly = 0) { npcRepository.insert(any()) }
        coVerify(exactly = 0) { chatParticipantRepository.addParticipant(any(), any(), any()) }
    }

    private fun assertNpcName(npc: NpcEntity, expected: String) {
        org.junit.Assert.assertEquals(expected, npc.name)
    }
}
