package com.kitsune.core.network.storycard

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

private const val TAG = "GenerateStoryCardUC"

private val CARD_KEYS = listOf("preset", "directive", "sceneTitle", "scenario", "firstMessage")

/**
 * One card produced from the player's own words.
 *
 * [presetId] is deliberately a **choice from a closed list** rather than nine separate enum values:
 * asking a model to fill nine independent enums invites invalid members and incoherent combinations
 * (a "reader" involvement paired with a "co-author" ending), whereas picking one of six curated
 * recipes cannot produce a combination nobody designed. Everything the recipes cannot express goes
 * into [directive], which the contract already ranks above every preset.
 */
data class StoryCardDraft(
    val presetId: String,
    val directive: String,
    val sceneTitle: String,
    val scenario: String,
    val firstMessage: String
)

private fun systemPrompt(presetIds: List<String>, characterContext: String) = """
You help a player set up a roleplay story before it starts. From a short free-form description of
what they want to experience, produce the story's framing and its opening scene.
${if (characterContext.isBlank()) "" else "The story stars this character:\n$characterContext"}
Respond with a single JSON object only, no markdown, using exactly these keys: "preset", "directive", "sceneTitle", "scenario", "firstMessage".
Rules:
- "preset" must be exactly one of: ${presetIds.joinToString(", ")}. Pick the one closest to what the player described. Never invent another value.
  - slow_romance: close, unhurried, emotionally uncertain two-hander.
  - dark_passion: obsession, power imbalance, control; heavy and adult.
  - adventure: events, movement, discovery, brisk exchanges.
  - light_comedy: misunderstandings, bad timing, absurd escalation.
  - long_saga: a broad story over time, recurring cast and factions.
  - chamber_piece: one place, interior detail, contemplative and slow.
- "directive" captures what the preset cannot: the specific thing this player asked for, rewritten as
  a concrete instruction to the writer ("she stays cold with him until he apologises", "never resolve
  an argument in the same scene"). One or two sentences. Leave it as an empty string if the preset
  already covers everything they said — do not pad it.
- "sceneTitle" is a short label for the opening situation, at most 40 characters.
- "scenario" sets the situation in a few sentences: where the story starts, what is happening, the
  dynamic between the character and the player.
- "firstMessage" opens the story already in motion, never a greeting or a self-introduction: ground it
  in a concrete physical scene, have the character take an active first move that involves the player,
  and end on an open beat that invites a reply.
- Never mention or imply an age for anyone.
- "directive" is re-sent to the writer on every single message of this story. Keep it free of any
  wording about young people or explicit anatomy — describe what you mean another way.
"""

/**
 * Turns "what do you feel like living?" into a filled story card, in **one** LLM call (2026-08-23).
 *
 * ## Why one call, and why here
 *
 * The memory pipeline works under an explicit "zero extra LLM calls" rule, and this is the one place
 * where breaking it pays for itself: the call happens **once per conversation**, not once per turn, so
 * it is amortised over the forty-plus messages that follow. A per-turn director doing the same job
 * would roughly double the running cost of the whole app — which is exactly why the previous
 * "satellite" memory systems were deleted in migration v18→v19.
 *
 * ## Why it also writes the opening
 *
 * The framing and the first message are the same decision. Generating them separately would mean two
 * calls and, worse, an opening written before the framing existed — which is precisely the defect
 * being fixed elsewhere in this change, where the opening scene was the one message produced with no
 * style contract at all.
 *
 * Reuses `SCENE_GEN` as its operation type rather than introducing a new one: a new string would fall
 * through the backend's routing `when` to no model enforcement **and** be billed to the user, since it
 * would not appear in `CostCalculator.FREE_OPERATION_TYPES`.
 */
class GenerateStoryCardUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver,
    private val appLanguageManager: AppLanguageManager
) {
    private val languageSuffix: String
        get() = "\nWrite every text field in ${appLanguageManager.getSelectedLanguage().nativeName} by " +
            "default — unless the description below is clearly written in a different language, in " +
            "which case match that language instead."

    /**
     * @param presetIds the preset catalogue, passed in rather than imported so `core:network` keeps
     *   knowing nothing about `feature:chat`.
     * @param characterContext the persona sheet, or blank for an ensemble scene.
     */
    suspend operator fun invoke(
        presetIds: List<String>,
        characterContext: String,
        description: String
    ): Result<StoryCardDraft> {
        if (description.isBlank()) return Result.failure(IllegalArgumentException("Describe the story first"))
        if (presetIds.isEmpty()) return Result.failure(IllegalArgumentException("No preset to choose from"))

        Log.d(TAG, "invoke: descriptionLength=${description.length}, presets=${presetIds.size}")
        val completion = chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.QUICK_GENERATION),
            systemPrompt = systemPrompt(presetIds, characterContext).trim() + languageSuffix,
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = description)),
            operationType = "SCENE_GEN",
            maxTokens = ChatCompletionRepository.GENERATION_MAX_TOKENS,
            allowContinuation = false
        )
        val result = completion.getOrElse { e ->
            // Failed before or during the call itself, so no credit was billed — see
            // GenerationFailureCategory.
            Log.e(TAG, "invoke: LLM call failed: ${e::class.simpleName}: ${e.message}", e)
            return Result.failure(e)
        }

        return try {
            val json = AiJsonParser.parseObject(result.content, knownKeys = CARD_KEYS)
            val rawPreset = json.stringField("preset").trim()
            val draft = StoryCardDraft(
                // A hallucinated preset id would otherwise resolve to nothing and leave the chat
                // unsteered — the exact state this whole feature exists to prevent. Falling back to
                // the first offered preset keeps the story framed even when the model ignores the
                // closed list.
                presetId = presetIds.firstOrNull { it.equals(rawPreset, ignoreCase = true) } ?: presetIds.first(),
                directive = json.stringField("directive").trim(),
                sceneTitle = json.stringField("sceneTitle").trim(),
                scenario = json.stringField("scenario").trim(),
                firstMessage = json.stringField("firstMessage").trim()
            )
            Log.d(TAG, "invoke: parsed card successfully (preset=${draft.presetId})")
            Result.success(draft)
        } catch (e: Exception) {
            // The model did answer, so the credit is already spent; this is our parsing failing.
            // Never log the generated content itself, only its length.
            Log.e(TAG, "invoke: response received but failed to parse (contentLength=${result.content.length})", e)
            Result.failure(GenerationParsingException("Réponse de l'IA invalide : ${e.message}", e))
        }
    }
}
