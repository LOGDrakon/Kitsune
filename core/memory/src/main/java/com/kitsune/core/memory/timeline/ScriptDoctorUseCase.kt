package com.kitsune.core.memory.timeline

import com.kitsune.core.data.local.entities.KeyMomentEntity
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.KeyMomentRepository
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import javax.inject.Inject

class ScriptDoctorUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val keyMomentRepository: KeyMomentRepository,
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver
) {

    suspend fun suggestDirections(chatId: String): List<String>? {
        val chat = chatRepository.getById(chatId) ?: return null
        val moments = keyMomentRepository.getByChat(chatId)
        val modelId = llmModelResolver.resolve(LlmOperation.CHAT)

        val userPrompt = buildString {
            if (chat.summary.isNotBlank()) {
                appendLine("Story summary:")
                appendLine(chat.summary)
                appendLine()
            }
            if (moments.isNotEmpty()) {
                appendLine("Key moments so far:")
                moments.forEach { appendLine("- ${it.title}: ${it.summary}") }
                appendLine()
            }
            appendLine("Based on the story above, suggest 3 possible directions the story could take next. For each, write a 1-2 sentence pitch.")
        }

        val result = chatCompletionRepository.complete(
            modelId = modelId,
            systemPrompt = SYSTEM_PROMPT.trim(),
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = userPrompt)),
            operationType = "SCRIPT_DOCTOR",
            maxTokens = 600
        )

        return result.getOrNull()?.content?.trim()?.takeIf { it.isNotBlank() }?.lines()?.filter { it.isNotBlank() }
    }

    private companion object {
        private const val SYSTEM_PROMPT = """
You are a story direction assistant for roleplay fiction.
Based on the current story state, suggest 3 different possible directions for what could happen next —
one could be a twist, one an emotional arc, one a new conflict, or any other genuinely distinct angle.
Prefer options that create momentum, tension, character development, or new possibilities over bland
restatements of the current scene.
Respond with exactly 3 suggestions, each on its own line, prefixed with a number (1. 2. 3.). Keep each
suggestion to 1-2 sentences.
Write in the same language as the story context.
        """
    }
}