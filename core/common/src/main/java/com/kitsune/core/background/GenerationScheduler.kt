package com.kitsune.core.background

/**
 * Entry point for scheduling AI generation work that must survive the user leaving the app.
 * Implementations are expected to enqueue a WorkManager worker and return the local job id
 * (which matches the id stored in `GenerationJobEntity`).
 */
interface GenerationScheduler {

    suspend fun schedulePersonaGeneration(request: PersonaGenerationRequest): String

    suspend fun scheduleUniverseGeneration(request: UniverseGenerationRequest): String

    suspend fun scheduleNpcGeneration(request: NpcGenerationRequest): String
}

data class PersonaGenerationRequest(
    val description: String,
    val templateStyleHint: String? = null,
    val proposalCount: Int = 1,
    val universeId: String? = null,
    val chatId: String? = null
)

data class UniverseGenerationRequest(
    val description: String,
    val proposalCount: Int = 1
)

data class NpcGenerationRequest(
    val universeContext: String,
    val description: String,
    val chatId: String? = null,
    val universeId: String? = null,
    val joinFraming: String? = null
)
