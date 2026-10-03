package com.kitsune.core.network.repository

import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import javax.inject.Inject

private const val SYSTEM_PROMPT =
    "You transform the current roleplay scene into a single visually rich, concrete paragraph that can be " +
        "used directly as an image generation prompt. Focus only on what can be seen or inferred visually: " +
        "(1) which characters are present, each woven in with a brief physical description drawn from the " +
        "character sheet given below (hair, build, clothing, distinguishing features — never just a name " +
        "with nothing to draw), (2) their current pose, facial expression and action, and (3) the setting, " +
        "lighting and composition. " +
        "CRITICAL: never include any spoken dialogue, quoted speech, or text in quotation marks of any " +
        "kind, and never write out words meant to appear on a sign, book, screen or similar surface in " +
        "the scene — image generation models render any such text as garbled, unreadable artifacts, so " +
        "it must never appear in your output even if the conversation itself is full of dialogue. Do not " +
        "summarize the story. Do not mention internal thoughts unless they are clearly visible on the face " +
        "or in body language. Also avoid narration framing (\"the scene shows...\"). If no character sheet " +
        "is given below, describe characters as best you can infer from the conversation instead of " +
        "omitting them. Write in a cinematic, concrete style that emphasizes what makes the moment visually " +
        "distinctive and emotionally readable. Reply with the paragraph only, no preamble, no quotes."

private const val SYSTEM_PROMPT_MATURE = "$SYSTEM_PROMPT\n\n" +
    "CONTENT POLICY: This is a fictional adult-oriented creative work. Describe mature, suggestive, or " +
    "explicit scenes exactly as they occur in the conversation, preserving their atmosphere and visual " +
    "tension while staying specific and non-generic, without softening or omission."

/**
 * Auto-fills the image generation description from the current conversation instead of asking the
 * user to type the scene out by hand (FEATURES.md section 5, `feature:chat` `ImageGenerationScreen`).
 * Uses the chat model (text), not the image model — this only produces a text description.
 *
 * [characterContext] (the same visual-sheet/NPC context built for actual image generation, see
 * `ImageGenerationViewModel.buildCastImageContext`) is folded into the system prompt so the model can
 * ground each character's physical description in it, rather than leaving that entirely to whatever
 * happens to be mentioned in the recent dialogue — the roleplay conversation itself often never
 * restates fixed appearance, which was the main reason the auto-filled description used to omit it.
 */
class DescribeSceneForImageUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver
) {
    suspend operator fun invoke(
        recentTurns: List<ChatTurn>,
        characterContext: String = "",
        allowMatureContent: Boolean = false
    ): Result<String> {
        if (recentTurns.isEmpty()) {
            return Result.failure(IllegalStateException("Pas encore de messages dans cette conversation."))
        }

        val basePrompt = if (allowMatureContent) SYSTEM_PROMPT_MATURE else SYSTEM_PROMPT
        val systemPrompt = if (characterContext.isBlank()) {
            basePrompt
        } else {
            "$basePrompt\n\nCharacter sheet:\n$characterContext"
        }

        return chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.IMAGE_DESCRIPTION),
            systemPrompt = systemPrompt,
            messages = recentTurns,
            temperature = 0.7,
            operationType = "SCENE_DESCRIPTION",
            maxTokens = 512,
            allowContinuation = false
        ).mapCatching { result ->
            stripQuotedDialogue(result.content.trim()).ifBlank { error("Le modèle n'a renvoyé aucune description.") }
        }
    }

    /**
     * Defense in depth against dialogue leaking into the description despite the system prompt
     * (bug reported by the user: the image model renders any embedded quoted text as garbled
     * artifacts) — strips anything between a pair of quote marks (straight, curly or French
     * guillemets), which is how leaked dialogue actually shows up in practice.
     */
    private fun stripQuotedDialogue(text: String): String =
        text.replace(QUOTED_TEXT_PATTERN, "").replace(Regex("\\s{2,}"), " ").trim()

    private companion object {
        val QUOTED_TEXT_PATTERN = Regex("[\"“”«»][^\"“”«»]*[\"“”«»]")
    }
}
