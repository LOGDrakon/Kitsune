package com.kitsune.core.network.visualsheet

/**
 * Builds the `characterContext` passed to [com.kitsune.core.network.repository.GenerateImageUseCase]
 * for a given persona: prefers the structured [PersonaVisualSheet] (FEATURES.md section 5) when one
 * is available, so physical traits/art style/palette/outfit stay identical across separate
 * generations, falling back to the old free-text name+description pairing for personas that don't
 * have a sheet yet (should be rare in practice — see [EnsurePersonaVisualSheetUseCase], which
 * backfills one transparently before this is normally called).
 */
fun buildPersonaImageContext(name: String, shortDescription: String, visualSheetJson: String?): String =
    PersonaVisualSheet.decodeOrNull(visualSheetJson)?.takeUnless { it.isBlank }?.toPromptContext(name)
        ?: "$name: $shortDescription"
