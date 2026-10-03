package com.kitsune.feature.universe.create

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.kitsune.core.background.FactionResult
import com.kitsune.core.background.GenerationScheduler
import com.kitsune.core.background.LocationResult
import com.kitsune.core.background.NpcResult
import com.kitsune.core.background.UniverseBundleResult
import com.kitsune.core.background.UniverseGenerationResult
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.GenerationJobType
import com.kitsune.core.data.repository.FactionRepository
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.data.repository.LocationRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.UniverseRepository
import com.kitsune.core.network.repository.FactionDraft
import com.kitsune.core.network.repository.GenerateWorldElementUseCase
import com.kitsune.core.network.repository.LocationDraft
import com.kitsune.core.network.repository.NpcDraft
import com.kitsune.core.network.repository.UniverseBundleDraft
import com.kitsune.core.network.repository.UniverseDraft
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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

private fun bundle(name: String) = UniverseBundleDraft(
    universe = UniverseDraft(name = name, description = "d", genre = "fantasy", visualStyle = "painterly"),
    factions = listOf(FactionDraft("Faction A", "d", "guild", "neutral"), FactionDraft("Faction B", "d", "cult", "evil")),
    locations = listOf(LocationDraft("Location A", "d", "city"), LocationDraft("Location B", "d", "forest")),
    npcs = listOf(NpcDraft("Npc A", "d", "p", "role", "phys"), NpcDraft("Npc B", "d", "p", "role", "phys"), NpcDraft("Npc C", "d", "p", "role", "phys"))
)

@OptIn(ExperimentalCoroutinesApi::class)
class UniverseCreationViewModelTest {

    private val universeRepository = mockk<UniverseRepository>()
    private val factionRepository = mockk<FactionRepository>()
    private val locationRepository = mockk<LocationRepository>()
    private val npcRepository = mockk<NpcRepository>()
    private val generateWorldElementUseCase = mockk<GenerateWorldElementUseCase>()
    private val generationScheduler = mockk<GenerationScheduler>(relaxed = true)
    private val generationJobRepository = mockk<GenerationJobRepository>(relaxed = true)
    private val backendClient = mockk<KitsuneBackendClient>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coJustRun { universeRepository.insert(any()) }
        coJustRun { factionRepository.insert(any()) }
        coJustRun { locationRepository.insert(any()) }
        coJustRun { npcRepository.insert(any()) }
        every { generationJobRepository.observeById(any()) } returns emptyFlow()
        // Relaxed mocking of a generic StateFlow<Boolean> property is unreliable (type erasure can
        // make MockK hand back a mock object where a real Boolean is expected) — stub explicitly.
        every { backendClient.isKitsunePlus } returns MutableStateFlow(false)
        every { backendClient.firstCreationFree } returns MutableStateFlow(false)
        every { universeRepository.getAll() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = UniverseCreationViewModel(
        context,
        savedStateHandle,
        universeRepository, factionRepository, locationRepository, npcRepository,
        generateWorldElementUseCase, generationScheduler, generationJobRepository, backendClient
    )

    private fun succeededUniverseJob(jobId: String, vararg bundles: UniverseBundleResult): GenerationJobEntity =
        GenerationJobEntity(
            id = jobId,
            type = GenerationJobType.UNIVERSE,
            state = GenerationJobState.SUCCEEDED,
            description = "test",
            resultJson = Json.encodeToString(UniverseGenerationResult(proposals = bundles.toList())),
            createdAt = 0L
        )

    private fun bundleResult(name: String) = UniverseBundleResult(
        universeName = name,
        universeDescription = "d",
        universeGenre = "fantasy",
        visualStyle = "painterly",
        factions = listOf(FactionResult("Faction A", "d", "guild", "neutral"), FactionResult("Faction B", "d", "cult", "evil")),
        locations = listOf(LocationResult("Location A", "d", "city"), LocationResult("Location B", "d", "forest")),
        npcs = listOf(NpcResult("Npc A", "d", "p", "role"), NpcResult("Npc B", "d", "p", "role"), NpcResult("Npc C", "d", "p", "role"))
    )

    @Test
    fun `generating proposals switches to scheduled state`() = runTest {
        coEvery { generationScheduler.scheduleUniverseGeneration(any()) } returns "job-3"

        val viewModel = viewModel()
        viewModel.updateAiDescription("floating cities")
        viewModel.updateProposalCount(3)
        viewModel.generateProposals()

        val state = viewModel.state.value
        assertTrue(state is UniverseCreationUiState.Scheduled)
        assertEquals("job-3", (state as UniverseCreationUiState.Scheduled).jobId)
    }

    @Test
    fun `a scheduling failure surfaces as an error`() = runTest {
        coEvery { generationScheduler.scheduleUniverseGeneration(any()) } throws RuntimeException("network error")

        val viewModel = viewModel()
        viewModel.updateProposalCount(3)
        viewModel.generateProposals()

        assertTrue(viewModel.state.value is UniverseCreationUiState.GenerationError)
    }

    @Test
    fun `loading a draft switches to browsing with all successful bundles`() = runTest {
        coEvery { generationJobRepository.observeById("draft-1") } returns flowOf(
            succeededUniverseJob(
                "draft-1",
                bundleResult("Aurelia"),
                bundleResult("Nox"),
                bundleResult("Veyra")
            )
        )

        val viewModel = viewModel(SavedStateHandle(mapOf("draftJobId" to "draft-1")))

        val state = viewModel.state.value
        assertTrue(state is UniverseCreationUiState.Browsing)
        assertEquals(3, (state as UniverseCreationUiState.Browsing).proposals.size)
    }

    @Test
    fun `selecting a draft proposal persists the universe and its nested factions, locations and npcs`() = runTest {
        coEvery { generationJobRepository.observeById("draft-1") } returns flowOf(
            succeededUniverseJob("draft-1", bundleResult("Aurelia"))
        )

        val viewModel = viewModel(SavedStateHandle(mapOf("draftJobId" to "draft-1")))
        viewModel.selectCurrentProposal()

        io.mockk.coVerify(exactly = 1) { universeRepository.insert(withArg { assertEquals("Aurelia", it.name) }) }
        io.mockk.coVerify(exactly = 2) { factionRepository.insert(any()) }
        io.mockk.coVerify(exactly = 2) { locationRepository.insert(any()) }
        io.mockk.coVerify(exactly = 3) { npcRepository.insert(any()) }
    }

    @Test
    fun `saved draft proposals are tracked so the same card cannot be saved twice`() = runTest {
        coEvery { generationJobRepository.observeById("draft-1") } returns flowOf(
            succeededUniverseJob("draft-1", bundleResult("Aurelia"))
        )

        val viewModel = viewModel(SavedStateHandle(mapOf("draftJobId" to "draft-1")))
        viewModel.selectCurrentProposal()
        viewModel.selectCurrentProposal()

        io.mockk.coVerify(exactly = 1) { universeRepository.insert(any()) }
        val state = viewModel.state.value as UniverseCreationUiState.Browsing
        assertEquals(setOf(0), state.savedIndices)
    }

    @Test
    fun `manual save with no AI involved persists a universe with no nested entities`() = runTest {
        val viewModel = viewModel()
        viewModel.updateName("Manual Universe")

        viewModel.save { }

        io.mockk.coVerify(exactly = 1) { universeRepository.insert(withArg { assertEquals("Manual Universe", it.name) }) }
        io.mockk.coVerify(exactly = 0) { factionRepository.insert(any()) }
        io.mockk.coVerify(exactly = 0) { npcRepository.insert(any()) }
    }
}
