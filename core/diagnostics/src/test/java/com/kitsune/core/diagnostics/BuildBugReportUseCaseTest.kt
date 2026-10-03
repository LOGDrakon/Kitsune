package com.kitsune.core.diagnostics

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.GenerationJobType
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.MessageAuditLogEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.data.repository.MessageAuditLogRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.network.preferences.NetworkPreferences
import com.kitsune.core.security.lock.AutoLockManager
import com.kitsune.core.security.profile.UserProfile
import com.kitsune.core.security.profile.UserProfileStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHAT_ID = "chat-1"

class BuildBugReportUseCaseTest {

    private val messageAuditLogRepository = mockk<MessageAuditLogRepository>()
    private val chatRepository = mockk<ChatRepository>()
    private val personaRepository = mockk<PersonaRepository>()
    private val generationJobRepository = mockk<GenerationJobRepository>()
    private val networkPreferences = mockk<NetworkPreferences> {
        every { getDefaultChatModelId() } returns "gpt-4.1"
        every { getDefaultImageModelId() } returns "gemini-3.1-flash-image-preview"
        every { getDefaultTemperature() } returns 0.9
    }
    private val userProfileStore = mockk<UserProfileStore>()
    private val autoLockManager = mockk<AutoLockManager> { every { timeoutSeconds } returns 300 }

    private val useCase = BuildBugReportUseCase(
        messageAuditLogRepository,
        chatRepository,
        personaRepository,
        generationJobRepository,
        networkPreferences,
        userProfileStore,
        autoLockManager
    )

    private fun persona() = PersonaEntity(
        id = "persona-1",
        universeId = null,
        name = "Aria",
        shortDescription = "desc",
        personality = "personality",
        scenario = "scenario",
        firstMessage = "hi",
        exampleDialogues = "",
        age = 25,
        maturityTags = listOf(MaturityTag.NSFW),
        visualSheetJson = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun chat() = ChatEntity(
        id = CHAT_ID,
        universeId = null,
        personaId = "persona-1",
        title = "Conversation 1",
        mode = ChatMode.CHAT,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun auditEntry(role: MessageRole, content: String, index: Int) = MessageAuditLogEntity(
        id = "audit-$index",
        chatId = CHAT_ID,
        messageId = "msg-$index",
        role = role,
        content = content,
        createdAt = index.toLong()
    )

    @Test
    fun `includes the user-provided subject and description`() = runTest {
        every { userProfileStore.get() } returns UserProfile()

        val report = useCase(subject = "Image generation fails", description = "The image never comes back", chatId = null)

        assertTrue(report.contains("Image generation fails"))
        assertTrue(report.contains("The image never comes back"))
    }

    @Test
    fun `redacts the user's configured name everywhere in the transcript`() = runTest {
        every { userProfileStore.get() } returns UserProfile(firstName = "Kenzo", lastName = "")
        coEvery { chatRepository.getById(CHAT_ID) } returns chat()
        coEvery { personaRepository.getById("persona-1") } returns persona()
        coEvery { messageAuditLogRepository.getByChatId(CHAT_ID) } returns listOf(
            auditEntry(MessageRole.USER, "Hello, I'm Kenzo and I love this app", 1),
            auditEntry(MessageRole.ASSISTANT, "Nice to meet you Kenzo!", 2)
        )

        val report = useCase(subject = "Subject", description = "Description", chatId = CHAT_ID)

        assertFalse(report.contains("Kenzo"))
        assertTrue(report.contains("[Utilisateur]"))
    }

    @Test
    fun `general report without a chatId has no transcript section content`() = runTest {
        every { userProfileStore.get() } returns UserProfile()

        val report = useCase(subject = "Subject", description = "Description", chatId = null)

        assertTrue(report.contains("aucune conversation jointe"))
    }

    @Test
    fun `attaches a failed generation job's category and credit-consumed status`() = runTest {
        every { userProfileStore.get() } returns UserProfile()
        coEvery { generationJobRepository.getById("job-1") } returns GenerationJobEntity(
            id = "job-1",
            type = GenerationJobType.PERSONA,
            state = GenerationJobState.FAILED,
            description = "a grumpy pirate",
            proposalCount = 3,
            errorMessage = "API error 451: blocked",
            errorCategory = "CONTENT_POLICY",
            createdAt = 0L,
            completedAt = 1000L
        )

        val report = useCase(subject = "Subject", description = "Description", jobId = "job-1")

        assertTrue(report.contains("## Génération échouée"))
        assertTrue(report.contains("a grumpy pirate"))
        assertTrue(report.contains("filtre de contenu"))
        assertTrue(report.contains("Crédit consommé pour cette tentative : Non"))
    }

    @Test
    fun `marks credit as consumed for a parsing failure since the LLM already answered`() = runTest {
        every { userProfileStore.get() } returns UserProfile()
        coEvery { generationJobRepository.getById("job-2") } returns GenerationJobEntity(
            id = "job-2",
            type = GenerationJobType.UNIVERSE,
            state = GenerationJobState.FAILED,
            description = "a floating city",
            proposalCount = 1,
            errorMessage = "Réponse de l'IA invalide : missing field 'name'",
            errorCategory = "PARSING_FAILED",
            createdAt = 0L,
            completedAt = 1000L
        )

        val report = useCase(subject = "Subject", description = "Description", jobId = "job-2")

        assertTrue(report.contains("Crédit consommé pour cette tentative : Oui"))
    }
}
