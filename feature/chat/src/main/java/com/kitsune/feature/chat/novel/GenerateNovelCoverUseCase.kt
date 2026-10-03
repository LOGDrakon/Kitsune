package com.kitsune.feature.chat.novel

import com.kitsune.core.network.repository.GenerateImageUseCase
import com.kitsune.core.network.repository.ImageGenerationRefusedException
import com.kitsune.core.network.repository.SoftenImagePromptUseCase
import javax.inject.Inject

private const val COVER_ASPECT_RATIO = "3:4"
private const val MAX_SUMMARY_CHARS_IN_PROMPT = 600

/** Labels the call in debug logs. */
private const val OPERATION_TYPE = "NOVEL_COVER"

/**
 * Generates a single illustrated book-cover image for the "Export Roman Illustré" PDF
 * ([ExportNovelPdfUseCase]) — never lets a refusal or failure break the export itself: returns
 * null on any unrecoverable failure, and [ExportNovelPdfUseCase] falls back to its original
 * avatar-row cover in that case, exactly as it already falls back to a single "Chapitre 1" when
 * there are no recorded key moments. Automatically retries once through [SoftenImagePromptUseCase]
 * on a content-policy refusal, mirroring `ImageGenerationViewModel.generateSfwFallback()` — but
 * transparently, since a book export has no UI moment for the user to trigger that retry manually
 * mid-export.
 */
class GenerateNovelCoverUseCase @Inject constructor(
    private val generateImageUseCase: GenerateImageUseCase,
    private val softenImagePromptUseCase: SoftenImagePromptUseCase
) {
    suspend operator fun invoke(
        title: String,
        storySummary: String,
        characterContext: String,
        allowMatureContent: Boolean
    ): ByteArray? {
        val prompt = buildCoverPrompt(title, storySummary)
        val result = generateImageUseCase(
            characterContext = characterContext,
            description = prompt,
            allowMatureContent = allowMatureContent,
            aspectRatio = COVER_ASPECT_RATIO,
            operationType = OPERATION_TYPE
        )
        result.getOrNull()?.firstOrNull()?.let { return it }
        if (result.exceptionOrNull() !is ImageGenerationRefusedException) return null

        val softened = softenImagePromptUseCase(prompt, characterContext).getOrNull() ?: return null
        return generateImageUseCase(
            characterContext = characterContext,
            description = softened,
            allowMatureContent = false,
            aspectRatio = COVER_ASPECT_RATIO,
            operationType = OPERATION_TYPE
        ).getOrNull()?.firstOrNull()
    }

    private fun buildCoverPrompt(title: String, storySummary: String): String = buildString {
        append("A striking, professional book cover illustration for a novel titled \"$title\". ")
        if (storySummary.isNotBlank()) {
            append("The story: ${storySummary.take(MAX_SUMMARY_CHARS_IN_PROMPT)}. ")
        }
        append(
            "Painterly, atmospheric cover art with dramatic lighting and composition befitting the " +
                "genre. No visible text, no typography, no title lettering rendered in the image itself."
        )
    }
}
