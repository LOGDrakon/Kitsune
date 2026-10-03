package com.kitsune.feature.persona.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.background.GenerationScheduler
import com.kitsune.core.background.PersonaGenerationRequest
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobType
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.diagnostics.SubmitBugReportUseCase
import com.kitsune.core.security.storage.EncryptedImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val BUG_REPORT_MIN_DIALOG_MS = 4000L

@HiltViewModel
class PersonaListViewModel @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val generationJobRepository: GenerationJobRepository,
    private val generationScheduler: GenerationScheduler,
    private val submitBugReportUseCase: SubmitBugReportUseCase,
    private val encryptedImageStore: EncryptedImageStore
) : ViewModel() {

    val personas: StateFlow<List<PersonaEntity>> = personaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val generationJobs: StateFlow<List<GenerationJobEntity>> =
        generationJobRepository.observeByType(GenerationJobType.PERSONA)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _avatarBytesById = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val avatarBytesById: StateFlow<Map<String, ByteArray>> = _avatarBytesById

    /** True while a bug report attached to a failed generation job is being redacted/sent — see
     * [reportFailedJob]. */
    private val _bugReportSending = MutableStateFlow(false)
    val bugReportSending: StateFlow<Boolean> = _bugReportSending.asStateFlow()

    fun ensureAvatarsLoaded(personas: List<PersonaEntity>) {
        val missingIds = personas.mapNotNull { it.avatarImageId }.filter { it !in _avatarBytesById.value }
        if (missingIds.isEmpty()) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                missingIds.mapNotNull { id -> encryptedImageStore.load(id)?.let { id to it } }
            }
            _avatarBytesById.value = _avatarBytesById.value + loaded
        }
    }

    /** Re-schedules the exact same request a failed job was created with (description, style hint,
     * proposal count) — the old job row is dropped first so it doesn't linger in the failed list
     * alongside the new attempt. */
    fun retryFailedJob(job: GenerationJobEntity) {
        viewModelScope.launch {
            generationJobRepository.deleteById(job.id)
            generationScheduler.schedulePersonaGeneration(
                PersonaGenerationRequest(
                    description = job.description,
                    templateStyleHint = job.templateStyleHint,
                    proposalCount = job.proposalCount,
                    universeId = job.universeId,
                    chatId = job.chatId
                )
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
