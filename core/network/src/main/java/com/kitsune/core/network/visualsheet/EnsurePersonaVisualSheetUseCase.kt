package com.kitsune.core.network.visualsheet

import javax.inject.Inject

/**
 * Backfills a structured visual sheet for personas created before this system existed (FEATURES.md
 * section 5), transparently and only once: if [existingVisualSheetJson] already decodes to a valid,
 * non-blank [PersonaVisualSheet] it's kept as-is ([Outcome.wasGenerated] false); otherwise a new one
 * is generated from [name]/[shortDescription]/[personality] via [GenerateVisualSheetUseCase].
 *
 * Never throws and never touches persistence itself (`core:network` doesn't depend on `core:data`):
 * on generation failure [existingVisualSheetJson] is returned unchanged. Callers are responsible for
 * persisting [Outcome.visualSheetJson] when [Outcome.wasGenerated] is true, so every later image
 * generation for that persona reuses the exact same description (see [buildPersonaImageContext]).
 */
class EnsurePersonaVisualSheetUseCase @Inject constructor(
    private val generateVisualSheetUseCase: GenerateVisualSheetUseCase
) {
    suspend operator fun invoke(
        name: String,
        shortDescription: String,
        personality: String,
        existingVisualSheetJson: String?
    ): Outcome {
        if (PersonaVisualSheet.decodeOrNull(existingVisualSheetJson)?.isBlank == false) {
            return Outcome(existingVisualSheetJson, wasGenerated = false)
        }

        val generated = generateVisualSheetUseCase(name, shortDescription, personality).getOrNull()
            ?: return Outcome(existingVisualSheetJson, wasGenerated = false)

        return Outcome(generated.encode(), wasGenerated = true)
    }

    data class Outcome(val visualSheetJson: String?, val wasGenerated: Boolean)
}
