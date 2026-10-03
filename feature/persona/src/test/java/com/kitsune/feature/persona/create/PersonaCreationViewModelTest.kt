package com.kitsune.feature.persona.create

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.kitsune.core.background.GenerationScheduler
import com.kitsune.core.background.PersonaGenerationResult
import com.kitsune.core.background.PersonaProposalResult
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.GenerationJobType
import com.kitsune.core.data.local.entities.NpcEntity
import com.kitsune.core.data.repository.ChatParticipantRepository
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.network.visualsheet.PersonaVisualSheet
import com.kitsune.core.network.persona.GenerateQuickPersonaUseCase
import com.kitsune.core.network.persona.PersonaDraft
import com.kitsune.core.security.locale.AppLanguage
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.profile.UserProfile
import com.kitsune.core.security.profile.UserProfileStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun draft(name: String) = PersonaDraft(
    name = name,
    description = "A description",
    personality = "A personality",
    scenario = "A scenario",
    firstMessage = "Hello",
    exampleDialogues = "",
    visualSheet = PersonaVisualSheet.EMPTY
)

@OptIn(ExperimentalCoroutinesApi::class)
class PersonaCreationViewModelTest {

    private val generateQuickPersonaUseCase = mockk<GenerateQuickPersonaUseCase>()
    private val personaRepository = mockk<PersonaRepository>()
    private val chatParticipantRepository = mockk<ChatParticipantRepository>()
    private val npcRepository = mockk<NpcRepository>()
    private val generationScheduler = mockk<GenerationScheduler>(relaxed = true)
    private val generationJobRepository = mockk<GenerationJobRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val appLanguageManager = mockk<AppLanguageManager>()
    private val userProfileStore = mockk<UserProfileStore>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Relaxed mocking of a generic StateFlow<Boolean> property is unreliable (type erasure can
        // make MockK hand back a mock object where a real Boolean is expected) — stub explicitly.
        every { personaRepository.observeAll() } returns flowOf(emptyList())
        every { appLanguageManager.getSelectedLanguage() } returns AppLanguage.ENGLISH
        // Non-blank by default so the "first persona requires profile setup" gate (see init)
        // doesn't divert these existing creation-flow tests — that gate has its own test below.
        every { userProfileStore.get() } returns UserProfile(firstName = "Test")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = PersonaCreationViewModel(
        context,
        savedStateHandle,
        generateQuickPersonaUseCase,
        personaRepository,
        chatParticipantRepository,
        npcRepository,
        generationScheduler,
        generationJobRepository,
        appLanguageManager,
        userProfileStore
    )

    private fun succeededPersonaJob(
        jobId: String,
        vararg proposals: PersonaProposalResult
    ): GenerationJobEntity = GenerationJobEntity(
        id = jobId,
        type = GenerationJobType.PERSONA,
        state = GenerationJobState.SUCCEEDED,
        description = "test",
        resultJson = Json.encodeToString(PersonaGenerationResult(proposals = proposals.toList())),
        createdAt = 0L
    )

    @Test
    fun `generate schedules a single proposal and moves to scheduled state`() = runTest {
        coEvery { generationScheduler.schedulePersonaGeneration(any()) } returns "job-1"

        val viewModel = viewModel()
        viewModel.generate("a hero", template = null, count = 1)

        val state = viewModel.state.value
        assertTrue(state is PersonaCreationUiState.Scheduled)
        assertEquals("job-1", (state as PersonaCreationUiState.Scheduled).jobId)
    }

    @Test
    fun `generate schedules several proposals and moves to scheduled state`() = runTest {
        coEvery { generationScheduler.schedulePersonaGeneration(any()) } returns "job-3"

        val viewModel = viewModel()
        viewModel.generate("a hero", template = null, count = 3)

        val state = viewModel.state.value
        assertTrue(state is PersonaCreationUiState.Scheduled)
        assertEquals("job-3", (state as PersonaCreationUiState.Scheduled).jobId)
    }

    @Test
    fun `generate surfaces an error when scheduling fails`() = runTest {
        coEvery { generationScheduler.schedulePersonaGeneration(any()) } throws RuntimeException("network error")

        val viewModel = viewModel()
        viewModel.generate("a hero", template = null, count = 1)

        assertTrue(viewModel.state.value is PersonaCreationUiState.GenerationError)
    }

    @Test
    fun `loading a draft with one proposal skips straight to review`() = runTest {
        coEvery { generationJobRepository.observeById("job-1") } returns flowOf(
            succeededPersonaJob("job-1", PersonaProposalResult("Aria", "desc", "personality", "scenario", "hello", ""))
        )

        val viewModel = viewModel(SavedStateHandle(mapOf("draftJobId" to "job-1")))

        val state = viewModel.state.value
        assertTrue(state is PersonaCreationUiState.Review)
        assertEquals("Aria", (state as PersonaCreationUiState.Review).draft.name)
    }

    @Test
    fun `loading a draft with several proposals lands in browsing`() = runTest {
        coEvery { generationJobRepository.observeById("job-2") } returns flowOf(
            succeededPersonaJob(
                "job-2",
                PersonaProposalResult("Aria", "desc", "personality", "scenario", "hello", ""),
                PersonaProposalResult("Bram", "desc", "personality", "scenario", "hello", ""),
                PersonaProposalResult("Cora", "desc", "personality", "scenario", "hello", "")
            )
        )

        val viewModel = viewModel(SavedStateHandle(mapOf("draftJobId" to "job-2")))

        val state = viewModel.state.value
        assertTrue(state is PersonaCreationUiState.Browsing)
        assertEquals(3, (state as PersonaCreationUiState.Browsing).proposals.size)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `navigating the carousel moves between draft proposals`() = runTest {
        coEvery { generationJobRepository.observeById("job-2") } returns flowOf(
            succeededPersonaJob(
                "job-2",
                PersonaProposalResult("Aria", "desc", "personality", "scenario", "hello", ""),
                PersonaProposalResult("Bram", "desc", "personality", "scenario", "hello", "")
            )
        )

        val viewModel = viewModel(SavedStateHandle(mapOf("draftJobId" to "job-2")))
        viewModel.showNextProposal()

        val state = viewModel.state.value as PersonaCreationUiState.Browsing
        assertEquals(1, state.currentIndex)
        assertEquals("Bram", state.proposals[state.currentIndex].name)
    }

    @Test
    fun `selecting a draft proposal reuses the visual sheet generated alongside it and moves to review`() = runTest {
        coEvery { generationJobRepository.observeById("job-2") } returns flowOf(
            succeededPersonaJob(
                "job-2",
                PersonaProposalResult("Aria", "desc", "personality", "scenario", "hello", ""),
                PersonaProposalResult(
                    "Bram", "desc", "personality", "scenario", "hello", "",
                    physicalTraits = "tall", artStyle = "", colorPalette = "", defaultOutfit = ""
                )
            )
        )

        val viewModel = viewModel(SavedStateHandle(mapOf("draftJobId" to "job-2")))
        viewModel.showNextProposal()
        viewModel.selectCurrentProposal()

        val state = viewModel.state.value
        assertTrue(state is PersonaCreationUiState.Review)
        assertEquals("Bram", (state as PersonaCreationUiState.Review).draft.name)
        assertEquals("tall", state.visualSheet.physicalTraits)
    }

    @Test
    fun `saving a proposal from a multi-proposal batch returns to browsing so more can be saved`() = runTest {
        coEvery { generationJobRepository.observeById("job-2") } returns flowOf(
            succeededPersonaJob(
                "job-2",
                PersonaProposalResult("Aria", "desc", "personality", "scenario", "hello", ""),
                PersonaProposalResult("Bram", "desc", "personality", "scenario", "hello", "")
            )
        )
        coEvery { personaRepository.upsert(any()) } returns Unit

        val viewModel = viewModel(SavedStateHandle(mapOf("draftJobId" to "job-2")))
        viewModel.selectCurrentProposal()
        viewModel.updateAge("25")
        var savedCallbackInvoked = false
        viewModel.save { savedCallbackInvoked = true }

        val state = viewModel.state.value
        assertTrue(state is PersonaCreationUiState.Browsing)
        assertEquals(setOf(0), (state as PersonaCreationUiState.Browsing).savedIndices)
        assertTrue(!savedCallbackInvoked)
        io.mockk.coVerify(exactly = 1) { personaRepository.upsert(any()) }

        // The user can now pick the second proposal and save it too, without leaving the flow.
        viewModel.showNextProposal()
        viewModel.selectCurrentProposal()
        viewModel.updateAge("30")
        viewModel.save { savedCallbackInvoked = true }

        val finalState = viewModel.state.value as PersonaCreationUiState.Browsing
        assertEquals(setOf(0, 1), finalState.savedIndices)
        assertTrue(!savedCallbackInvoked)
        io.mockk.coVerify(exactly = 2) { personaRepository.upsert(any()) }
    }

    @Test
    fun `saving the single-proposal path still exits the flow via onSaved, unlike a multi-proposal batch`() = runTest {
        coEvery { generationJobRepository.observeById("job-1") } returns flowOf(
            succeededPersonaJob("job-1", PersonaProposalResult("Aria", "desc", "personality", "scenario", "hello", ""))
        )
        coEvery { personaRepository.upsert(any()) } returns Unit

        val viewModel = viewModel(SavedStateHandle(mapOf("draftJobId" to "job-1")))
        viewModel.updateAge("25")
        var savedId: String? = null
        viewModel.save { savedId = it }

        assertTrue(savedId != null)
    }

    @Test
    fun `pre-fills the description from an existing NPC when created from a chat cast`() = runTest {
        val npc = NpcEntity(
            id = "npc-1",
            universeId = "universe-1",
            name = "Old Man Jenkins",
            description = "A grumpy shopkeeper",
            personality = "Grumpy but fair",
            role = "Shopkeeper",
            factionId = null,
            locationId = null,
            age = null,
            createdAt = 0L,
            updatedAt = 0L
        )
        coEvery { npcRepository.getById("npc-1") } returns npc

        val viewModel = viewModel(SavedStateHandle(mapOf("fromNpcId" to "npc-1")))

        // init{} launches in viewModelScope — runTest's dispatcher runs it eagerly.
        assertTrue(viewModel.initialDescription.value.contains("Old Man Jenkins"))
    }

    @Test
    fun `first persona with a blank profile requires profile setup instead of the normal flow`() = runTest {
        every { userProfileStore.get() } returns UserProfile()

        val viewModel = viewModel()

        assertTrue(viewModel.state.value is PersonaCreationUiState.RequiresProfileSetup)
    }

    @Test
    fun `first persona with a filled profile goes straight to the normal flow`() = runTest {
        every { userProfileStore.get() } returns UserProfile(firstName = "Kenzo")

        val viewModel = viewModel()

        assertTrue(viewModel.state.value is PersonaCreationUiState.DescribeInput)
    }

    @Test
    fun `not the first persona skips the profile gate even with a blank profile`() = runTest {
        every { userProfileStore.get() } returns UserProfile()
        every { personaRepository.observeAll() } returns flowOf(
            listOf(mockk<com.kitsune.core.data.local.entities.PersonaEntity>())
        )

        val viewModel = viewModel()

        assertTrue(viewModel.state.value is PersonaCreationUiState.DescribeInput)
    }
}
