package com.kitsune.core.background

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.kitsune.core.common.generation.GenerationFailureCategory
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.network.repository.GenerateWorldElementUseCase
import com.kitsune.core.network.repository.GenerationParsingException
import com.kitsune.core.network.persona.GenerateQuickPersonaUseCase
import com.kitsune.core.network.persona.PersonaDraft
import com.kitsune.core.network.provider.ProviderHttpException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Maps a failure from the network layer to a user-facing category. Anything not explicitly
 * recognised — another HTTP error, an `IOException`/timeout — is [GenerationFailureCategory.TECHNICAL]. */
private fun classifyGenerationFailure(e: Throwable): GenerationFailureCategory = when {
    e is ProviderHttpException && e.code == 402 -> GenerationFailureCategory.INSUFFICIENT_CREDITS
    e is ProviderHttpException && (e.code == 451 || (e.code == 403 &&
        (e.body.contains("moderation", ignoreCase = true) || e.body.contains("flagged", ignoreCase = true)))) ->
        GenerationFailureCategory.CONTENT_POLICY
    e is GenerationParsingException -> GenerationFailureCategory.PARSING_FAILED
    else -> GenerationFailureCategory.TECHNICAL
}

@HiltWorker
class GenerationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val generationJobRepository: GenerationJobRepository,
    private val generateQuickPersonaUseCase: GenerateQuickPersonaUseCase,
    private val generateWorldElementUseCase: GenerateWorldElementUseCase,
    private val notificationHelper: GenerationNotificationHelper
) : CoroutineWorker(context, params) {

    private val jobId: String = inputData.getString(KEY_JOB_ID)
        ?: throw IllegalArgumentException("GenerationWorker requires a job id")

    override suspend fun doWork(): Result {
        return try {
            // Best-effort foreground promotion: on recent Android versions this can throw
            // (e.g. ForegroundServiceStartNotAllowedException when scheduled from background).
            // It must never crash the worker or the job would stay stuck as PENDING.
            runCatching { setForeground(createForegroundInfo()) }

            markRunning()

            when (GenerationType.valueOf(inputData.getString(KEY_TYPE)!!)) {
                GenerationType.PERSONA -> doGeneratePersona()
                GenerationType.UNIVERSE -> doGenerateUniverse()
                GenerationType.NPC -> doGenerateNpc()
            }
        } catch (e: Throwable) {
            Log.e("GenerationWorker", "Generation failed for job $jobId", e)
            val msg = e.message ?: e.localizedMessage ?: e::class.simpleName ?: "Erreur inconnue"
            markFailed(msg, classifyGenerationFailure(e))
            Result.failure()
        }
    }

    /** One line naming what a draft *is*, for the benefit of the next proposal in the batch. Short on
     *  purpose: the next call needs to recognise the territory to avoid, not re-read the whole sheet. */
    private fun PersonaDraft.conceptLine(): String =
        listOf(name, description).filter { it.isNotBlank() }.joinToString(" — ").take(160)

    private suspend fun doGeneratePersona(): Result {
        val description = inputData.getString(KEY_DESCRIPTION)!!
        val count = inputData.getInt(KEY_COUNT, 1)
        val styleHint = inputData.getString(KEY_STYLE_HINT)
        val prompt = if (styleHint != null) "$description\n\nStyle: $styleHint" else description

        Log.d("GenerationWorker", "doGeneratePersona: jobId=$jobId, count=$count, descriptionLength=${description.length}")
        // Sequential on purpose, and now load-bearing: each proposal is told what the previous ones
        // already were, so "invent something different" stops being an instruction about siblings the
        // call cannot see. Running these in parallel would be faster and would put the batch straight
        // back to converging on one modal character.
        val seenConcepts = mutableListOf<String>()
        val results = (0 until count).map { index ->
            val result = generateQuickPersonaUseCase(
                prompt,
                proposalIndex = index,
                proposalCount = count,
                alreadyProposed = seenConcepts.toList()
            )
            result.getOrNull()?.let { seenConcepts.add(it.conceptLine()) }
            result
        }
        val proposals = results.mapNotNull { it.getOrNull() }
        if (proposals.isEmpty()) {
            val firstException = results.firstNotNullOfOrNull { it.exceptionOrNull() }
            val error = firstException?.let { ex -> ex.message ?: ex.localizedMessage ?: ex::class.simpleName }
                ?: "La génération a échoué. Vérifiez votre connexion et réessayez."
            val category = firstException?.let { classifyGenerationFailure(it) } ?: GenerationFailureCategory.TECHNICAL
            Log.e("GenerationWorker", "Persona generation failed: category=$category, errors=${results.map { it.exceptionOrNull()?.let { ex -> "${ex::class.simpleName}: ${ex.message}" } }}")
            markFailed(error, category)
            return Result.failure()
        }
        if (proposals.size < count) {
            Log.i("GenerationWorker", "doGeneratePersona: ${count - proposals.size}/$count proposal(s) failed but continuing with ${proposals.size} successful one(s): ${results.mapNotNull { it.exceptionOrNull() }.map { "${it::class.simpleName}: ${it.message}" }}")
        }

        markSucceeded(
            PersonaGenerationResult(
                proposals = proposals.map {
                    PersonaProposalResult(
                        name = it.name,
                        description = it.description,
                        personality = it.personality,
                        scenario = it.scenario,
                        firstMessage = it.firstMessage,
                        exampleDialogues = it.exampleDialogues,
                        desire = it.desire,
                        fear = it.fear,
                        flaw = it.flaw,
                        moralLine = it.moralLine,
                        secret = it.secret,
                        physicalTraits = it.visualSheet.physicalTraits,
                        artStyle = it.visualSheet.artStyle,
                        colorPalette = it.visualSheet.colorPalette,
                        defaultOutfit = it.visualSheet.defaultOutfit
                    )
                }
            )
        )
        return Result.success()
    }

private suspend fun doGenerateUniverse(): Result {
        val description = inputData.getString(KEY_DESCRIPTION)!!
        val count = inputData.getInt(KEY_COUNT, 1)

        val results = (1..count).map { generateWorldElementUseCase.generateUniverseBundle(description) }
        Log.i("GenerationWorker", "Universe generation: count=$count, successes=${results.count { it.isSuccess }}, failures=${results.count { it.isFailure }}")
        results.forEachIndexed { i, r ->
            if (r.isFailure) {
                val ex = r.exceptionOrNull()
                Log.e("GenerationWorker", "Universe[$i] FAILED: ${ex?.javaClass?.name}: ${ex?.message}", ex)
            } else {
                val b = r.getOrNull()
                // Never log the generated universe's name — user-created content (compliance audit
                // 2026-08-04); counts alone are enough to confirm the bundle came back non-empty.
                Log.i("GenerationWorker", "Universe[$i] OK: factions=${b?.factions?.size}, locations=${b?.locations?.size}, npcs=${b?.npcs?.size}")
            }
        }
        val proposals = results.mapNotNull { it.getOrNull() }
        if (proposals.isEmpty()) {
            val firstException = results.firstNotNullOfOrNull { it.exceptionOrNull() }
            val error = firstException?.let { ex -> ex.message ?: ex.localizedMessage ?: ex::class.simpleName }
                ?: "La génération a échoué. Vérifiez votre connexion et réessayez."
            val category = firstException?.let { classifyGenerationFailure(it) } ?: GenerationFailureCategory.TECHNICAL
            Log.e("GenerationWorker", "Universe generation failed: no proposals. category=$category, errors=${results.map { it.exceptionOrNull()?.message }}")
            markFailed(error, category)
            return Result.failure()
        }

        Log.i("GenerationWorker", "Universe generation succeeded: ${proposals.size} proposals")
        markSucceeded(
            UniverseGenerationResult(
                proposals = proposals.map { bundle ->
                    UniverseBundleResult(
                        universeName = bundle.universe.name,
                        universeDescription = bundle.universe.description,
                        universeGenre = bundle.universe.genre,
                        visualStyle = bundle.universe.visualStyle,
                        factions = bundle.factions.map { FactionResult(it.name, it.description, it.type, it.alignment) },
                        locations = bundle.locations.map { LocationResult(it.name, it.description, it.type) },
                        npcs = bundle.npcs.map { NpcResult(it.name, it.description, it.personality, it.role, it.physicalDescription) }
                    )
                }
            )
        )
        return Result.success()
    }

private suspend fun doGenerateNpc(): Result {
        val universeContext = inputData.getString(KEY_UNIVERSE_CONTEXT)!!
        val description = inputData.getString(KEY_DESCRIPTION)!!

        val result = generateWorldElementUseCase.generateNpc(universeContext, description)
        val npc = result.getOrNull()
        if (npc == null) {
            val ex = result.exceptionOrNull()
            val msg = ex?.message ?: ex?.localizedMessage ?: "La génération a échoué."
            val category = ex?.let { classifyGenerationFailure(it) } ?: GenerationFailureCategory.TECHNICAL
            Log.e("GenerationWorker", "NPC generation failed: category=$category", ex)
            markFailed(msg, category)
            return Result.failure()
        }

        markSucceeded(
            NpcGenerationResult(
                npc = NpcResult(npc.name, npc.description, npc.personality, npc.role, npc.physicalDescription)
            )
        )
        return Result.success()
    }

    private suspend fun markRunning() {
        generationJobRepository.getById(jobId)?.let {
            generationJobRepository.upsert(it.copy(state = GenerationJobState.RUNNING))
        }
    }

    private suspend fun markSucceeded(result: Any) {
        val json = when (result) {
            is PersonaGenerationResult -> Json.encodeToString(result)
            is UniverseGenerationResult -> Json.encodeToString(result)
            is NpcGenerationResult -> Json.encodeToString(result)
            else -> throw IllegalStateException("Unknown result type")
        }
        Log.i("GenerationWorker", "markSucceeded: jobId=$jobId, jsonLen=${json.length}")
        generationJobRepository.getById(jobId)?.let {
            generationJobRepository.upsert(
                it.copy(
                    state = GenerationJobState.SUCCEEDED,
                    resultJson = json,
                    completedAt = System.currentTimeMillis()
                )
            )
        }
        notificationHelper.showCompleteNotification(jobId, succeeded = true)
        generationJobRepository.getById(jobId)?.let { update ->
            generationJobRepository.upsert(update.copy(notificationShown = true))
        }
    }

    private suspend fun markFailed(message: String, category: GenerationFailureCategory) {
        Log.i("GenerationWorker", "markFailed: jobId=$jobId, category=$category")
        generationJobRepository.getById(jobId)?.let {
            generationJobRepository.upsert(
                it.copy(
                    state = GenerationJobState.FAILED,
                    errorMessage = message,
                    errorCategory = category.name,
                    completedAt = System.currentTimeMillis()
                )
            )
        }
        notificationHelper.showCompleteNotification(jobId, succeeded = false)
        generationJobRepository.getById(jobId)?.let { update ->
            generationJobRepository.upsert(update.copy(notificationShown = true))
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            notificationHelper.foregroundNotificationId,
            notificationHelper.buildProgressNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        private const val KEY_JOB_ID = "jobId"
        private const val KEY_TYPE = "type"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_COUNT = "count"
        private const val KEY_STYLE_HINT = "styleHint"
        private const val KEY_UNIVERSE_CONTEXT = "universeContext"

        fun personaInput(jobId: String, request: PersonaGenerationRequest): Data =
            Data.Builder()
                .putString(KEY_JOB_ID, jobId)
                .putString(KEY_TYPE, GenerationType.PERSONA.name)
                .putString(KEY_DESCRIPTION, request.description)
                .putInt(KEY_COUNT, request.proposalCount)
                .putString(KEY_STYLE_HINT, request.templateStyleHint)
                .build()

        fun universeInput(jobId: String, request: UniverseGenerationRequest): Data =
            Data.Builder()
                .putString(KEY_JOB_ID, jobId)
                .putString(KEY_TYPE, GenerationType.UNIVERSE.name)
                .putString(KEY_DESCRIPTION, request.description)
                .putInt(KEY_COUNT, request.proposalCount)
                .build()

        fun npcInput(jobId: String, request: NpcGenerationRequest): Data =
            Data.Builder()
                .putString(KEY_JOB_ID, jobId)
                .putString(KEY_TYPE, GenerationType.NPC.name)
                .putString(KEY_DESCRIPTION, request.description)
                .putString(KEY_UNIVERSE_CONTEXT, request.universeContext)
                .build()
    }

    private enum class GenerationType { PERSONA, UNIVERSE, NPC }
}
