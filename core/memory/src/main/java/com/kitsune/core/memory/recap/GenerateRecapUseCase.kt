package com.kitsune.core.memory.recap

import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.storyContentOnly
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.memory.timeline.BuildStoryChronologyUseCase
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import javax.inject.Inject

private const val RECAP_MAX_MESSAGES = 6
private const val RECAP_INACTIVITY_THRESHOLD_MS = 24L * 60 * 60 * 1000

/** Last beats of the ordered ledger fed to the recap — the "previously on" of a TV episode recaps
 *  landmark moments, not the last few lines of dialogue. */
private const val RECAP_CHRONOLOGY_ENTRIES = 3

class GenerateRecapUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val buildStoryChronologyUseCase: BuildStoryChronologyUseCase,
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver
) {

    fun shouldRecap(lastUpdatedAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        return (now - lastUpdatedAt) >= RECAP_INACTIVITY_THRESHOLD_MS
    }

    suspend fun generate(chatId: String, chatSummary: String): String? {
        val recentMessages = messageRepository.getRecent(chatId, RECAP_MAX_MESSAGES).storyContentOnly()
        if (recentMessages.isEmpty() && chatSummary.isBlank()) return null

        val modelId = llmModelResolver.resolve(LlmOperation.SUMMARY)

        // The original design for this feature (IDEAS.md 2026-07-28, idea A) specified the timeline
        // as an input; it shipped with only the summary and the last few messages, which is why a
        // recap could open on whatever mundane exchange happened to be last. Free to add: the
        // ledger is two local reads, and this reuses the RECAP call that already runs.
        val chronology = runCatching {
            buildStoryChronologyUseCase(chatId, RECAP_CHRONOLOGY_ENTRIES)
        }.getOrDefault(emptyList())

        val userPrompt = buildString {
            if (chatSummary.isNotBlank()) {
                appendLine("Story summary so far:")
                appendLine(chatSummary)
                appendLine()
            }
            if (chronology.isNotEmpty()) {
                appendLine("Most recent landmark moments:")
                chronology.forEach { appendLine(it) }
                appendLine()
            }
            if (recentMessages.isNotEmpty()) {
                appendLine("Most recent messages:")
                recentMessages.forEach { msg ->
                    val speaker = when (msg.role) {
                        MessageRole.USER -> "User"
                        MessageRole.ASSISTANT -> "Character"
                        MessageRole.SYSTEM -> "System"
                        // Filtered out upstream by storyContentOnly(); labelled rather than crashed on if one ever slips through.
                        MessageRole.STYLE_DIRECTIVE -> "Style directive"
                    }
                    appendLine("$speaker: ${msg.content.take(300)}")
                }
            }
        }

        val result = chatCompletionRepository.complete(
            modelId = modelId,
            systemPrompt = SYSTEM_PROMPT.trim(),
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = userPrompt)),
            operationType = "RECAP",
            maxTokens = 512
        )

        return result.getOrNull()?.content?.trim()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val SYSTEM_PROMPT = """
You write a concise "Previously on..." recap for a roleplay story after a long pause.
Summarize the most important recent developments, relationships, unresolved tensions, locations, and
emotional beats so the player can re-enter the scene immediately. Lead with the landmark moments
rather than with whatever was said last — the point is to restore where the story stands, not to
replay the final exchange.
Keep it compact, vivid, and continuity-friendly — 2-4 sentences, no over-explaining, no irrelevant details.
Do not add any commentary, just the recap text. Start directly with the narration — no "Previously" prefix needed.
Write in the same language as the story.
        """
    }
}