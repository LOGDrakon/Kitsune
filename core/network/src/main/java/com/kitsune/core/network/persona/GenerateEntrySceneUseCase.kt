package com.kitsune.core.network.persona

import android.util.Log
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.json.AiJsonParser
import com.kitsune.core.network.json.AiJsonParser.stringField
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.network.repository.GenerationParsingException
import com.kitsune.core.security.locale.AppLanguageManager
import javax.inject.Inject

private const val TAG = "GenerateEntrySceneUC"

private val SCENE_KEYS = listOf("title", "scenario", "firstMessage")

private fun systemPrompt(characterContext: String) = """
You are a creative assistant that designs alternative entry scenes ("starting points") for an
existing roleplay character, so the player can begin the story from a different situation than the
character's default one. Here is the character this scene is for:
$characterContext
From a short free-form description of the desired scene, invent a complete, coherent entry scene
consistent with this character.
Respond with a single JSON object only, no markdown, using exactly these keys: "title", "scenario", "firstMessage".
Rules:
- Every field must be filled.
- "title" is a short label for this scene (at most 40 characters), shown in a list of scenes to pick from.
- "scenario" sets the situation/context in a few sentences: where the story starts, what's happening, the dynamic between the character and the player.
- "firstMessage" opens the story already in motion, not a greeting or a self-introduction: ground it in
  a concrete physical scene (where the character is, what they're doing, a sensory detail of the
  moment), have the character take an active first move that directly involves the player — approach
  them, address them, hand them something, interrupt what they're doing — rather than passively waiting
  or describing themselves, and end on an open beat (a question, an unfinished action, a charged pause)
  that invites the player to respond, never a closed statement that could just as well end the scene.
- Stay true to the character's established personality and voice.
- Never mention or imply an age for the character.
"""

/**
 * Powers the "Générer rapide par IA" button in entry scene creation (`PersonaDetailScreen`'s
 * `CreateSceneDialog`) — mirrors `GenerateQuickPersonaUseCase`/`GenerateWorldElementUseCase`'s
 * prompt/JSON-parsing shape, except the character itself is fixed context (like a faction/location/
 * NPC's `universeContext`) rather than something being generated.
 */
class GenerateEntrySceneUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver,
    private val appLanguageManager: AppLanguageManager
) {
    private val languageSuffix: String
        get() = "\nWrite every field in ${appLanguageManager.getSelectedLanguage().nativeName} by default — " +
            "unless the description below is clearly written in a different language, in which case match " +
            "that language instead."

    suspend operator fun invoke(characterContext: String, description: String): Result<SceneDraft> {
        if (description.isBlank()) return Result.failure(IllegalArgumentException("Describe the scene first"))

        Log.d(TAG, "invoke: descriptionLength=${description.length}")
        val completion = chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.QUICK_GENERATION),
            systemPrompt = systemPrompt(characterContext).trim() + languageSuffix,
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = description)),
            operationType = "SCENE_GEN",
            maxTokens = ChatCompletionRepository.GENERATION_MAX_TOKENS,
            allowContinuation = false
        )
        val result = completion.getOrElse { e ->
            // Failed before/during the LLM call itself — never bills a credit, see
            // GenerationFailureCategory's doc comment.
            Log.e(TAG, "invoke: LLM call failed: ${e::class.simpleName}: ${e.message}", e)
            return Result.failure(e)
        }

        return try {
            val json = AiJsonParser.parseObject(result.content, knownKeys = SCENE_KEYS)
            val draft = SceneDraft(
                title = json.stringField("title").trim(),
                scenario = json.stringField("scenario"),
                firstMessage = json.stringField("firstMessage")
            )
            Log.d(TAG, "invoke: parsed scene successfully")
            Result.success(draft)
        } catch (e: Exception) {
            // The LLM DID respond successfully here — this failure is purely on the app's parsing
            // side, after the backend already billed the credit for this call. Never log the raw
            // generated content itself (persona/scenario text) — only its length, consistent with
            // this app's security-first, nothing-sensitive-in-logs policy (compliance audit
            // 2026-08-04, mirroring BUGS.md BUG-064 finding #9's server-side equivalent).
            Log.e(TAG, "invoke: response received but failed to parse (contentLength=${result.content.length})", e)
            Result.failure(GenerationParsingException("Réponse de l'IA invalide : ${e.message}", e))
        }
    }
}

data class SceneDraft(val title: String, val scenario: String, val firstMessage: String)
