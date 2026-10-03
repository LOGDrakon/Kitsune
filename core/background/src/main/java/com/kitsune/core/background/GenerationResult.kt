package com.kitsune.core.background

import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Serialisable result payloads stored in `GenerationJobEntity.resultJson`.
 * They are intentionally decoupled from the UI/room models so the core:network use cases can change
 * without touching the worker table schema.
 */
@Serializable
data class PersonaGenerationResult(
    val proposals: List<PersonaProposalResult>
)

@Serializable
data class PersonaProposalResult(
    val name: String,
    val description: String,
    val personality: String,
    val scenario: String,
    val firstMessage: String,
    val exampleDialogues: String,
    /** Ressorts intimes (2026-08-22). Défauts vides : un job déjà en file avant cette version doit
     *  continuer à se désérialiser, pas échouer à la reprise. */
    val desire: String = "",
    val fear: String = "",
    val flaw: String = "",
    val moralLine: String = "",
    val secret: String = "",
    val physicalTraits: String = "",
    val artStyle: String = "",
    val colorPalette: String = "",
    val defaultOutfit: String = ""
)

@Serializable
data class UniverseGenerationResult(
    val proposals: List<UniverseBundleResult>
)

@Serializable
data class UniverseBundleResult(
    val universeName: String,
    val universeDescription: String,
    val universeGenre: String,
    val visualStyle: String,
    val factions: List<FactionResult>,
    val locations: List<LocationResult>,
    val npcs: List<NpcResult>
)

@Serializable
data class NpcGenerationResult(
    val npc: NpcResult
)

@Serializable
data class FactionResult(
    val name: String,
    val description: String,
    val type: String,
    val alignment: String
)

@Serializable
data class LocationResult(
    val name: String,
    val description: String,
    val type: String
)

@Serializable
data class NpcResult(
    val name: String,
    val description: String,
    val personality: String,
    val role: String,
    val physicalDescription: String = ""
)

/** Only tries to parse a result that has actually succeeded; returns null otherwise. */
fun GenerationJobEntity.parsePersonaResult(): PersonaGenerationResult? {
    val json = resultJson?.takeIf { it.isNotBlank() } ?: return null
    return takeIf { it.state == GenerationJobState.SUCCEEDED }
        ?.let { Json.decodeFromString<PersonaGenerationResult>(json) }
}

fun GenerationJobEntity.parseUniverseResult(): UniverseGenerationResult? {
    val json = resultJson?.takeIf { it.isNotBlank() } ?: return null
    return takeIf { it.state == GenerationJobState.SUCCEEDED }
        ?.let { Json.decodeFromString<UniverseGenerationResult>(json) }
}

fun GenerationJobEntity.parseNpcResult(): NpcGenerationResult? {
    val json = resultJson?.takeIf { it.isNotBlank() } ?: return null
    return takeIf { it.state == GenerationJobState.SUCCEEDED }
        ?.let { Json.decodeFromString<NpcGenerationResult>(json) }
}
