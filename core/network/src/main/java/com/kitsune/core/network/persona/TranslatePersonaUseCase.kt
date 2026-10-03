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
import com.kitsune.core.security.locale.AppLanguage
import javax.inject.Inject

private const val TAG = "TranslatePersonaUC"

private val KNOWN_KEYS = listOf("description", "personality", "scenario", "firstMessage", "exampleDialogues")

private val SYSTEM_PROMPT = """
You are a professional literary translator working on an adult roleplay character sheet.
Translate every field into the requested target language, preserving tone, register, personality, and
narrative voice exactly — this is a translation, not a rewrite: do not summarize, embellish, soften, or
otherwise change the content, only its language. Keep any markdown-style emphasis (*actions*, **names**)
and formatting exactly where it already is.
Respond with a single JSON object only, no markdown, using exactly these keys:
"description", "personality", "scenario", "firstMessage", "exampleDialogues".
Every field must be filled — never leave a field empty or return the source text unchanged unless it is
already in the target language.
""".trimIndent()

/** Result of translating a persona's sheet + opening message — same field set as the subset of
 * [PersonaDraft] that's actually free-form prose (name/tags/visual sheet are left untouched: a name
 * generally shouldn't be translated, and the visual sheet describes appearance, not language). */
data class PersonaTranslation(
    val shortDescription: String,
    val personality: String,
    val scenario: String,
    val firstMessage: String,
    val exampleDialogues: String
)

/**
 * Translates an existing persona's sheet/opening message into [targetLanguage] — offered on
 * `PersonaDetailScreen` when the persona's `contentLanguage` doesn't match the app's current
 * language (or on demand regardless, since `contentLanguage` is only a best-effort hint for
 * personas created before it existed).
 *
 * A single call translating all 5 fields together (like [GenerateQuickPersonaUseCase], one call
 * per action) keeps this cheap — it's a free/untracked-cost operation server-side
 * (`CostCalculator.FREE_OPERATION_TYPES`), but still a real LLM call with real cost, so it's never
 * looped or retried automatically.
 */
class TranslatePersonaUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver
) {
    suspend operator fun invoke(
        shortDescription: String,
        personality: String,
        scenario: String,
        firstMessage: String,
        exampleDialogues: String,
        targetLanguage: AppLanguage
    ): Result<PersonaTranslation> {
        val userMessage = buildUserMessage(shortDescription, personality, scenario, firstMessage, exampleDialogues)
        val completion = chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.TRANSLATION),
            systemPrompt = "$SYSTEM_PROMPT\nTarget language: ${targetLanguage.nativeName} (${targetLanguage.languageTag}).",
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = userMessage)),
            operationType = "TRANSLATION",
            maxTokens = ChatCompletionRepository.GENERATION_MAX_TOKENS,
            allowContinuation = false
        )
        val result = completion.getOrElse { e ->
            Log.e(TAG, "invoke: LLM call failed: ${e::class.simpleName}: ${e.message}", e)
            return Result.failure(e)
        }

        return try {
            val json = AiJsonParser.parseObject(result.content, knownKeys = KNOWN_KEYS)
            Result.success(
                PersonaTranslation(
                    shortDescription = json.stringField("description"),
                    personality = json.stringField("personality"),
                    scenario = json.stringField("scenario"),
                    firstMessage = json.stringField("firstMessage"),
                    exampleDialogues = json.stringField("exampleDialogues")
                )
            )
        } catch (e: Exception) {
            // Never log the raw translated persona content itself — only its length (compliance
            // audit 2026-08-04, mirroring the same fix applied to every other generation use case).
            Log.e(TAG, "invoke: response received but failed to parse (contentLength=${result.content.length})", e)
            Result.failure(GenerationParsingException("Réponse de l'IA invalide : ${e.message}", e))
        }
    }

    // Kept as plain labelled text rather than JSON-in/JSON-out: the model only needs to read these
    // fields, not parse structured input, which keeps prompt tokens (and cost) down compared to
    // re-serializing as JSON.
    private fun buildUserMessage(
        shortDescription: String,
        personality: String,
        scenario: String,
        firstMessage: String,
        exampleDialogues: String
    ): String = """
        "description": $shortDescription
        "personality": $personality
        "scenario": $scenario
        "firstMessage": $firstMessage
        "exampleDialogues": $exampleDialogues
    """.trimIndent()
}
