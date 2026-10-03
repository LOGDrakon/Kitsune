package com.kitsune.core.data.local.entities

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * A persona's authored story tone, as it travels between installs (2026-08-24).
 *
 * ## Why a JSON blob rather than a typed field on the marketplace model
 *
 * Tone cards move along two paths — the encrypted local export and the marketplace — and the models
 * for those live in `feature:persona` and `core:backend` respectively. Neither of those modules can
 * see `core:data`'s enums, and `core:data` cannot see `core:backend`: there is no shared layer, and
 * inventing a dependency edge between the API-client layer and the database schema to get one would
 * be a worse trade than a string.
 *
 * `MarketplacePersonaData.visualSheetJson` already solves the identical problem the identical way, so
 * this follows the precedent rather than setting a second one. Both directions live here, next to the
 * entity, so a card cannot round-trip correctly through one path and lose a field through the other —
 * not hypothetical: the persona's inner drives shipped in the local export and not the marketplace
 * for a day, which is exactly that gap.
 *
 * ## Why enum names, not a preset reference
 *
 * The nine modes travel as plain names so a card written on one install reads exactly as its author
 * left it on another, whatever that install's built-in catalogue looks like. An unrecognised name
 * (a card authored on a newer version) resolves to the enum's first member — `DEFAULT` in every case
 * here — so it degrades to a milder register instead of refusing to import. Same lenient convention
 * as `Converters.safeValueOf`.
 */
@Serializable
data class ToneCardPayload(
    val name: String,
    val description: String = "",
    val basePresetId: String = "",
    val storyPaceMode: String = "DEFAULT",
    val toneMode: String = "DEFAULT",
    val involvementMode: String = "DEFAULT",
    val narrativeRhythmMode: String = "DEFAULT",
    val universeMode: String = "DEFAULT",
    val intensityMode: String = "DEFAULT",
    val replyLength: String = "DEFAULT",
    val narrationBalance: String = "DEFAULT",
    val voiceMode: String = "DEFAULT",
    val directive: String = ""
)

private val toneCardListSerializer = ListSerializer(ToneCardPayload.serializer())

private val toneCardJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private inline fun <reified T : Enum<T>> String.toModeOrDefault(): T =
    try {
        enumValueOf<T>(this)
    } catch (e: IllegalArgumentException) {
        enumValues<T>().first()
    }

fun ToneCardEntity.toPayload(): ToneCardPayload = ToneCardPayload(
    name = name,
    description = description,
    basePresetId = basePresetId,
    storyPaceMode = storyPaceMode.name,
    toneMode = toneMode.name,
    involvementMode = involvementMode.name,
    narrativeRhythmMode = narrativeRhythmMode.name,
    universeMode = universeMode.name,
    intensityMode = intensityMode.name,
    replyLength = replyLength.name,
    narrationBalance = narrationBalance.name,
    voiceMode = voiceMode.name,
    directive = directive
)

/**
 * @param personaId the **receiving** install's persona id — never the sender's, which does not exist
 *   here. A universe-scoped card passes [universeId] instead.
 */
fun ToneCardPayload.toEntity(
    personaId: String? = null,
    universeId: String? = null,
    createdAt: Long = System.currentTimeMillis()
): ToneCardEntity = ToneCardEntity(
    id = UUID.randomUUID().toString(),
    personaId = personaId,
    universeId = universeId,
    name = name,
    description = description,
    basePresetId = basePresetId,
    storyPaceMode = storyPaceMode.toModeOrDefault(),
    toneMode = toneMode.toModeOrDefault(),
    involvementMode = involvementMode.toModeOrDefault(),
    narrativeRhythmMode = narrativeRhythmMode.toModeOrDefault(),
    universeMode = universeMode.toModeOrDefault(),
    intensityMode = intensityMode.toModeOrDefault(),
    replyLength = replyLength.toModeOrDefault(),
    narrationBalance = narrationBalance.toModeOrDefault(),
    voiceMode = voiceMode.toModeOrDefault(),
    directive = directive,
    createdAt = createdAt
)

/** Encodes a persona's tone cards for transport. Returns null for an empty list so a persona with no
 *  tones carries no field at all rather than an empty array. */
fun encodeToneCards(cards: List<ToneCardEntity>): String? {
    if (cards.isEmpty()) return null
    return toneCardJson.encodeToString(toneCardListSerializer, cards.map { it.toPayload() })
}

/**
 * Decodes tone cards received from an export file or a marketplace listing.
 *
 * Never throws: a malformed or truncated blob yields no tones rather than failing the whole persona
 * import. Losing the tones is recoverable by hand; losing the character is not.
 */
fun decodeToneCards(json: String?): List<ToneCardPayload> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching { toneCardJson.decodeFromString(toneCardListSerializer, json) }.getOrDefault(emptyList())
}
