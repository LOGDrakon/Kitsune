package com.kitsune.core.network.suggestions

import android.util.Log
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.security.locale.AppLanguageManager
import javax.inject.Inject

private const val TAG = "GenerateNextReplySuggestionsUC"

private const val SUGGESTION_COUNT = 3

/** Matches a leading list marker ("- ", "* ", "• ") a model might still prepend despite the system
 * prompt asking for plain lines — stripped before quote-trimming so a marker-then-quote line like
 * `- "Turn around."` doesn't leave the opening quote behind (trimming only the marker chars first
 * would stop at the space between the marker and the quote). */
private val LEADING_LIST_MARKER_REGEX = Regex("^[-•*]+\\s*")

private const val SYSTEM_PROMPT = """
You are helping a user find words for their own next line in an ongoing interactive-fiction
roleplay chat. Given the recent conversation below, suggest exactly 3 short, distinct options
(roughly 5-15 words each) for what the user's own character could say or do next — written from
the user's point of view, ready to send as-is or lightly edited.
Rules:
- Each option must be genuinely different from the others (different tone, action, or direction).
- Write only the user's own line — no narration of what other characters do, no meta-commentary.
- Reply with exactly 3 lines, one option per line, no numbering, no bullet points, no quotation marks.
"""

/**
 * Powers an on-demand "what could I say next?" button next to the chat input — the user taps it
 * when they want a nudge, it is never called automatically after every AI turn (see BUG-070: an
 * unconditional extra LLM call on every turn already caused a production incident once, for
 * moderation — the same cost/latency/reliability caution applies here). Billed as a normal chat
 * turn like any other completion; unlike [com.kitsune.core.network.inspiration
 * .GenerateInspirationUseCase] this isn't a one-time creation-flow helper, it's repeatable on every
 * turn, so making it free would be a real monetization call for the app owner to make, not assumed
 * here.
 */
class GenerateNextReplySuggestionsUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver,
    private val appLanguageManager: AppLanguageManager
) {
    suspend operator fun invoke(recentMessages: List<ChatTurn>): Result<List<String>> {
        Log.d(TAG, "generate: recentMessages=${recentMessages.size}")
        if (recentMessages.isEmpty()) return Result.failure(IllegalArgumentException("No conversation to suggest from"))

        val languageSuffix = "\nWrite in ${appLanguageManager.getSelectedLanguage().nativeName}, " +
            "matching the language of the conversation above."
        val completion = chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.NEXT_REPLY_SUGGESTIONS),
            systemPrompt = SYSTEM_PROMPT.trim() + languageSuffix,
            messages = recentMessages,
            operationType = "NEXT_REPLY_SUGGESTIONS",
            maxTokens = ChatCompletionRepository.MEMORY_MAX_TOKENS,
            allowContinuation = false
        )
        return completion.map { result ->
            result.content.lines()
                .map { line ->
                    LEADING_LIST_MARKER_REGEX.replace(line.trim(), "").trim().trim('"', '“', '”').trim()
                }
                .filter { it.isNotBlank() }
                .take(SUGGESTION_COUNT)
        }.mapCatching { suggestions ->
            suggestions.ifEmpty { throw IllegalStateException("Model returned no usable suggestions") }
        }.onFailure { e -> Log.e(TAG, "generate: LLM call failed: ${e::class.simpleName}: ${e.message}", e) }
    }
}
