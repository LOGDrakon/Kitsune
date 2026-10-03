package com.kitsune.core.network.visualsheet

import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.json.AiJsonParser
import com.kitsune.core.network.json.AiJsonParser.stringField
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.security.locale.AppLanguageManager
import javax.inject.Inject

private val KNOWN_KEYS = listOf("physicalTraits", "artStyle", "colorPalette", "defaultOutfit")

private const val SYSTEM_PROMPT = """
You design a fixed, reusable visual reference sheet for a fictional character, so it can be
redrawn consistently across many separate image generations later on.
Your task is to define a visually memorable character identity that is specific enough to stay
stable, but flexible enough to remain useful in many scenes.
Respond with a single JSON object only, no markdown, using exactly these keys:
"physicalTraits", "artStyle", "colorPalette", "defaultOutfit".
Rules:
- Every field must be filled.
- "physicalTraits" must be concrete, detailed, and stable: build, height, hair color/style, eye
  color, skin tone, facial structure, distinguishing features, overall silhouette.
- "artStyle" must be a reusable rendering style that matches the character's tone and world (e.g.
  "semi-realistic digital painting, soft cinematic lighting").
- "colorPalette" must contain 3 to 5 dominant colors that reinforce the character's identity.
- "defaultOutfit" must be a specific outfit with clear materials, cuts, and accessories.
- Never mention or imply an age.
- Keep the description visually grounded, distinctive, and internally consistent.
"""

/**
 * Generates the structured visual sheet (FEATURES.md section 5) reused verbatim in every future
 * image-generation prompt for a persona, so its appearance stays consistent across generations.
 * Used both right after quick-persona creation (`feature:persona`) and as a lazy backfill for
 * personas created before this existed (see [EnsurePersonaVisualSheetUseCase]).
 */
class GenerateVisualSheetUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver,
    private val appLanguageManager: AppLanguageManager
) {
    suspend operator fun invoke(name: String, shortDescription: String, personality: String): Result<PersonaVisualSheet> {
        val characterSummary = buildString {
            appendLine("Name: $name")
            appendLine("Description: $shortDescription")
            if (personality.isNotBlank()) appendLine("Personality: $personality")
        }.trim()

        val languageHint = "Write every field in ${appLanguageManager.getSelectedLanguage().nativeName} by " +
            "default — unless the character description below is clearly written in a different language, in " +
            "which case match that language instead."

        return chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.VISUAL_SHEET),
            systemPrompt = "${SYSTEM_PROMPT.trim()}\n$languageHint",
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = characterSummary)),
            operationType = "VISUAL_SHEET",
            maxTokens = ChatCompletionRepository.GENERATION_MAX_TOKENS,
            allowContinuation = false
        ).mapCatching { result ->
            val json = AiJsonParser.parseObject(result.content, knownKeys = KNOWN_KEYS)
            PersonaVisualSheet(
                physicalTraits = json.stringField("physicalTraits"),
                artStyle = json.stringField("artStyle"),
                colorPalette = json.stringField("colorPalette"),
                defaultOutfit = json.stringField("defaultOutfit")
            )
        }
    }
}
