package com.kitsune.core.network.repository

import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import javax.inject.Inject

private const val SYSTEM_PROMPT = """
You rewrite an image generation prompt that was just refused by the image model's content policy
into a safe-for-work version that preserves as much of the original scene's composition, mood,
visual appeal and character intent as possible — the goal is a still-alluring, cinematic, purposeful
image, not a neutral or unrelated one.
Rules:
- Re-clothe any nudity. If the conversation context mentions a specific garment being removed
  earlier, put that same garment back on, but keep it suggestive (loosened, off-shoulder,
  disheveled, slipping) rather than prim or unrelated to the scene.
- Keep the pose, framing, lighting, atmosphere and styling cues recognizable and suggestive.
- Never describe explicit sexual acts, nudity, or genitals — replace disallowed details with
  elegant, tasteful, visually coherent alternatives that still convey the scene's tension and
  intimacy through pose, expression and framing.
- Keep it in the same language as the original prompt.
Reply with the rewritten prompt only — no preamble, no quotes, no explanation of what you changed.
"""

/**
 * Fallback for when [GenerateImageUseCase] throws [ImageGenerationRefusedException] — rewrites the
 * description into an SFW-but-still-suggestive version instead of just failing outright, so a scene
 * that would render as explicit can still get *an* image rather than none (FEATURES.md section 5).
 * This is not a jailbreak attempt: it makes the *request* comply with the model's own content
 * policy (softened wording) rather than trying to get explicit output past it.
 */
class SoftenImagePromptUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver
) {
    suspend operator fun invoke(originalDescription: String, characterContext: String): Result<String> {
        if (originalDescription.isBlank()) {
            return Result.failure(IllegalArgumentException("Nothing to soften — the original description is blank."))
        }

        val userPrompt = buildString {
            appendLine("Refused image prompt: $originalDescription")
            if (characterContext.isNotBlank()) {
                appendLine("Character context (for continuity, e.g. what they were wearing before): $characterContext")
            }
        }

        return chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.IMAGE_DESCRIPTION),
            systemPrompt = SYSTEM_PROMPT.trim(),
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = userPrompt)),
            temperature = 0.7,
            operationType = "SCENE_DESCRIPTION",
            maxTokens = 512,
            allowContinuation = false
        ).mapCatching { result ->
            result.content.trim().ifBlank { error("Le modèle n'a renvoyé aucune version adoucie.") }
        }
    }
}
