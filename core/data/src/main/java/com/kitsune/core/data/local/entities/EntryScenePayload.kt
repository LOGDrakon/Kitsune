package com.kitsune.core.data.local.entities

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * A persona's alternative starting points, as they travel between installs (2026-08-24).
 *
 * Entry scenes were the one piece of authored persona content that reached neither the export file
 * nor the marketplace: a character published with three carefully written openings arrived on the
 * buyer's device with none of them, silently. Since a scene is often *why* a persona is interesting —
 * it is what places the character in a situation rather than leaving them waiting to be greeted —
 * that was the most valuable thing the listing was dropping.
 *
 * Encoded as a JSON blob for the same reason as [ToneCardPayload], and following the same precedent
 * (`MarketplacePersonaData.visualSheetJson`): no module can see both `core:data` and `core:backend`,
 * and inventing that dependency edge to avoid a string would be the worse trade.
 */
@Serializable
data class EntryScenePayload(
    val title: String,
    val scenario: String = "",
    val firstMessage: String = ""
)

private val entrySceneListSerializer = ListSerializer(EntryScenePayload.serializer())

private val entrySceneJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun EntrySceneEntity.toPayload(): EntryScenePayload = EntryScenePayload(
    title = title,
    scenario = scenario,
    firstMessage = firstMessage
)

/** @param personaId the **receiving** install's persona id — never the sender's, which does not exist here. */
fun EntryScenePayload.toEntity(
    personaId: String,
    createdAt: Long = System.currentTimeMillis()
): EntrySceneEntity = EntrySceneEntity(
    id = UUID.randomUUID().toString(),
    personaId = personaId,
    title = title,
    scenario = scenario,
    firstMessage = firstMessage,
    createdAt = createdAt
)

/** Returns null for an empty list, so a persona with no scenes carries no field rather than `[]`. */
fun encodeEntryScenes(scenes: List<EntrySceneEntity>): String? {
    if (scenes.isEmpty()) return null
    return entrySceneJson.encodeToString(entrySceneListSerializer, scenes.map { it.toPayload() })
}

/**
 * Never throws: a malformed blob yields no scenes rather than failing the whole persona import.
 * Losing the scenes is recoverable by hand; losing the character is not.
 */
fun decodeEntryScenes(json: String?): List<EntryScenePayload> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching { entrySceneJson.decodeFromString(entrySceneListSerializer, json) }.getOrDefault(emptyList())
}
