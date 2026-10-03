package com.kitsune.core.network.repository

/** A selectable image aspect ratio — `value` is `null` for "Auto" (no `-ar` directive sent at all,
 * letting the image model pick its own default), otherwise a `"W:H"` ratio string. See
 * [GenerateImageUseCase] for why this is a prompt directive rather than a request field — Mammouth.ai's
 * API had no dedicated size/aspect-ratio parameter, and the backend's `/v1/chat/completions` contract
 * still doesn't carry one even though OpenRouter's own dedicated Images API does (`aspect_ratio`,
 * `resolution`); wiring that through would be a real feature change, not part of this migration.
 * Shared by every image generation entry point (in-chat, persona gallery) so they offer the same
 * options. */
data class AspectRatioOption(val label: String, val value: String?)

val ASPECT_RATIO_OPTIONS = listOf(
    AspectRatioOption("Auto", null),
    AspectRatioOption("Carré (1:1)", "1:1"),
    AspectRatioOption("Portrait (9:16)", "9:16"),
    AspectRatioOption("Paysage (16:9)", "16:9"),
    AspectRatioOption("Portrait (3:4)", "3:4"),
    AspectRatioOption("Paysage (4:3)", "4:3")
)
