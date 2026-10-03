package com.kitsune.core.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.GenerationJobType
import com.kitsune.core.data.repository.GenerationJobRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerGenerationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generationJobRepository: GenerationJobRepository
) : GenerationScheduler {

    override suspend fun schedulePersonaGeneration(request: PersonaGenerationRequest): String {
        val id = UUID.randomUUID().toString()
        val entity = GenerationJobEntity(
            id = id,
            type = GenerationJobType.PERSONA,
            state = GenerationJobState.PENDING,
            description = request.description,
            universeId = request.universeId,
            chatId = request.chatId,
            proposalCount = request.proposalCount,
            templateStyleHint = request.templateStyleHint,
            createdAt = System.currentTimeMillis()
        )
        enqueueJob(entity) {
            GenerationWorker.personaInput(id, request)
        }
        return id
    }

    override suspend fun scheduleUniverseGeneration(request: UniverseGenerationRequest): String {
        val id = UUID.randomUUID().toString()
        val entity = GenerationJobEntity(
            id = id,
            type = GenerationJobType.UNIVERSE,
            state = GenerationJobState.PENDING,
            description = request.description,
            proposalCount = request.proposalCount,
            createdAt = System.currentTimeMillis()
        )
        enqueueJob(entity) {
            GenerationWorker.universeInput(id, request)
        }
        return id
    }

    override suspend fun scheduleNpcGeneration(request: NpcGenerationRequest): String {
        val id = UUID.randomUUID().toString()
        val entity = GenerationJobEntity(
            id = id,
            type = GenerationJobType.NPC,
            state = GenerationJobState.PENDING,
            description = request.description,
            chatId = request.chatId,
            universeId = request.universeId,
            joinFraming = request.joinFraming,
            createdAt = System.currentTimeMillis()
        )
        enqueueJob(entity) {
            GenerationWorker.npcInput(id, request)
        }
        return id
    }

    private suspend fun enqueueJob(entity: GenerationJobEntity, inputBuilder: () -> Data) {
        // Persist PENDING before enqueueing so UI can show it immediately.
        generationJobRepository.upsert(entity)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<GenerationWorker>()
            .setInputData(inputBuilder())
            .setConstraints(constraints)
            .addTag("generation")
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
