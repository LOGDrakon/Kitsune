package com.kitsune.core.network.visualsheet

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val visualSheetCodec = Json { ignoreUnknownKeys = true }

/**
 * Structured visual reference for a persona (FEATURES.md section 5: "Fiche visuelle structurée par
 * persona ... réutilisée dans chaque prompt"), persisted as JSON in
 * `PersonaEntity.visualSheetJson` (core:data).
 *
 * Reused verbatim (via [toPromptContext]) in every image-generation prompt for that persona —
 * avatar or in-chat — so physical traits, art style, palette and default outfit stay identical
 * across separate generations instead of drifting each time the AI reinterprets a short free-text
 * description. This is the core of Kitsune's visual continuity system for characters in images.
 */
@Serializable
data class PersonaVisualSheet(
    val physicalTraits: String,
    val artStyle: String,
    val colorPalette: String,
    val defaultOutfit: String
) {
    /** True when every field is empty — treated the same as "no sheet at all" by callers. */
    val isBlank: Boolean
        get() = physicalTraits.isBlank() && artStyle.isBlank() && colorPalette.isBlank() && defaultOutfit.isBlank()

    /** Folded into `characterContext` for [com.kitsune.core.network.repository.GenerateImageUseCase]. */
    fun toPromptContext(name: String): String = buildString {
        appendLine("Character name: $name")
        if (physicalTraits.isNotBlank()) appendLine("Physical traits (must stay identical every time): $physicalTraits")
        if (artStyle.isNotBlank()) appendLine("Art style (must stay identical every time): $artStyle")
        if (colorPalette.isNotBlank()) appendLine("Color palette (must stay identical every time): $colorPalette")
        if (defaultOutfit.isNotBlank()) appendLine("Default outfit unless the request says otherwise: $defaultOutfit")
    }.trim()

    fun encode(): String = visualSheetCodec.encodeToString(serializer(), this)

    companion object {
        val EMPTY = PersonaVisualSheet(physicalTraits = "", artStyle = "", colorPalette = "", defaultOutfit = "")

        /** Returns null for null/blank/malformed input instead of throwing — always safe to call. */
        fun decodeOrNull(raw: String?): PersonaVisualSheet? {
            if (raw.isNullOrBlank()) return null
            return runCatching { visualSheetCodec.decodeFromString(serializer(), raw) }.getOrNull()
        }
    }
}
