package com.kitsune.feature.universe.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.background.GenerationScheduler
import com.kitsune.core.background.UniverseGenerationRequest
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobType
import com.kitsune.core.data.local.entities.UniverseEntity
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.data.repository.UniverseRepository
import com.kitsune.core.diagnostics.SubmitBugReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val BUG_REPORT_MIN_DIALOG_MS = 4000L

@HiltViewModel
class UniverseListViewModel @Inject constructor(
    private val universeRepository: UniverseRepository,
    private val generationJobRepository: GenerationJobRepository,
    private val generationScheduler: GenerationScheduler,
    private val submitBugReportUseCase: SubmitBugReportUseCase
) : ViewModel() {

    val universes: StateFlow<List<UniverseEntity>> = universeRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val generationJobs: StateFlow<List<GenerationJobEntity>> =
        generationJobRepository.observeByType(GenerationJobType.UNIVERSE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True while a bug report attached to a failed generation job is being redacted/sent — see
     * [reportFailedJob]. */
    private val _bugReportSending = MutableStateFlow(false)
    val bugReportSending: StateFlow<Boolean> = _bugReportSending.asStateFlow()

    fun deleteUniverse(universe: UniverseEntity) {
        viewModelScope.launch {
            runCatching { universeRepository.delete(universe) }
        }
    }

    /** Re-schedules the exact same request a failed job was created with (description, proposal
     * count) — the old job row is dropped first so it doesn't linger in the failed list alongside
     * the new attempt. */
    fun retryFailedJob(job: GenerationJobEntity) {
        viewModelScope.launch {
            generationJobRepository.deleteById(job.id)
            generationScheduler.scheduleUniverseGeneration(
                UniverseGenerationRequest(description = job.description, proposalCount = job.proposalCount)
            )
        }
    }

    fun dismissFailedJob(job: GenerationJobEntity) {
        viewModelScope.launch { generationJobRepository.deleteById(job.id) }
    }

    /** Submits a bug report with [jobId] attached so [com.kitsune.core.diagnostics.BuildBugReportUseCase]
     * includes the job's type/prompt/failure category/credit-consumed status automatically. */
    fun reportFailedJob(jobId: String, subject: String, description: String, onDone: () -> Unit) {
        if (_bugReportSending.value) return
        viewModelScope.launch {
            _bugReportSending.value = true
            val minDisplay = launch { delay(BUG_REPORT_MIN_DIALOG_MS) }
            submitBugReportUseCase(subject, description, jobId = jobId)
            minDisplay.join()
            _bugReportSending.value = false
            onDone()
        }
    }
}
