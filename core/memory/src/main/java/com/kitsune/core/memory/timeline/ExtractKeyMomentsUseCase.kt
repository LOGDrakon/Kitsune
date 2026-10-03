package com.kitsune.core.memory.timeline

import com.kitsune.core.data.local.entities.KeyMomentEntity
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.storyContentOnly
import com.kitsune.core.data.local.entities.MomentType
import com.kitsune.core.data.local.entities.StoryMood
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.KeyMomentRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.json.AiJsonParser
import com.kitsune.core.network.json.AiJsonParser.stringField
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import android.util.Log
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ExtractKeyMomentsUC"

class ExtractKeyMomentsUseCase @Inject constructor(
    private val keyMomentRepository: KeyMomentRepository,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver
) {

    suspend operator fun invoke(chatId: String, batch: List<MessageEntity>) {
        if (batch.isEmpty()) return

        val modelId = llmModelResolver.resolve(LlmOperation.SUMMARY)
        val existingMoments = keyMomentRepository.getByChat(chatId)
        val existingSummaries = existingMoments.map { it.summary }.toSet()

        val userPrompt = buildString {
            if (existingMoments.isNotEmpty()) {
                appendLine("Already captured key moments:")
                existingMoments.forEach { appendLine("- ${it.title}: ${it.summary}") }
                appendLine()
            }
            appendLine("New messages to analyze:")
            batch.storyContentOnly().forEach { msg ->
                val speaker = speakerLabel(msg)
                appendLine("$speaker: ${msg.content.take(500)}")
            }
        }

        val result = chatCompletionRepository.complete(
            modelId = modelId,
            systemPrompt = SYSTEM_PROMPT.trim(),
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = userPrompt)),
            operationType = "KEY_MOMENTS",
            maxTokens = 1024
        )

        val content = result.getOrNull()?.content ?: return
        val parsed = parseKeyMoments(content) ?: return

        // Chronology stamps, all from data already in hand — no extra call, no extra query beyond
        // the chat row. anchorCreatedAt orders the ledger; storyTimeLabel is the in-fiction clock
        // reading at the time of extraction; messageId anchors the moment back into the transcript
        // (BUG-105: it used to be hardcoded null, which silently collapsed the novel export's
        // chapter splitting to a single "Chapitre 1").
        val anchorCreatedAt = batch.last().createdAt
        val storyTimeLabel = chatRepository.getById(chatId)?.storyTimeAnchor.orEmpty()
        val anchorMessageId = batch.lastOrNull { it.role == MessageRole.ASSISTANT }?.id ?: batch.last().id

        var nextOrder = keyMomentRepository.getMaxOrder(chatId)
        for (moment in parsed) {
            if (moment.summary in existingSummaries) continue
            nextOrder++

            val snippets = batch.storyContentOnly().take(3).joinToString("\n---\n") { msg ->
                "${speakerLabel(msg)}: ${msg.content.take(300)}"
            }

            keyMomentRepository.upsert(
                KeyMomentEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    messageId = anchorMessageId,
                    momentType = moment.type,
                    mood = moment.mood,
                    title = moment.title,
                    summary = moment.summary,
                    snippets = snippets,
                    isAutoDetected = true,
                    createdAt = System.currentTimeMillis(),
                    momentOrder = nextOrder,
                    anchorCreatedAt = anchorCreatedAt,
                    storyTimeLabel = storyTimeLabel
                )
            )
        }
    }

    suspend fun addManualMoment(chatId: String, message: MessageEntity, title: String) {
        val nextOrder = keyMomentRepository.getMaxOrder(chatId) + 1
        keyMomentRepository.upsert(
            KeyMomentEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                messageId = message.id,
                momentType = MomentType.CUSTOM,
                mood = StoryMood.NEUTRAL,
                title = title.ifBlank { message.content.take(60) },
                summary = message.content.take(300),
                snippets = message.content,
                isAutoDetected = false,
                createdAt = System.currentTimeMillis(),
                momentOrder = nextOrder,
                anchorCreatedAt = message.createdAt,
                storyTimeLabel = chatRepository.getById(chatId)?.storyTimeAnchor.orEmpty()
            )
        )
    }

    private fun speakerLabel(msg: MessageEntity): String = when (msg.role) {
        MessageRole.USER -> "User"
        MessageRole.ASSISTANT -> "Character"
        MessageRole.SYSTEM -> "System"
        // Filtered out upstream by storyContentOnly(); labelled rather than crashed on if one ever slips through.
        MessageRole.STYLE_DIRECTIVE -> "Style directive"
    }

    private data class ParsedMoment(
        val type: MomentType,
        val mood: StoryMood,
        val title: String,
        val summary: String
    )

    /** Uses [AiJsonParser] like the lore path, rather than raw `org.json` between the first `[` and
     *  the last `]` — that hand-rolled scan returned null on any exception with zero trace, so a
     *  model emitting a markdown fence, a trailing comma or an unescaped quote silently produced no
     *  timeline at all. The response is wrapped in an object for the same reason (the parser's
     *  salvage pass is keyed on known object keys). */
    private fun parseKeyMoments(content: String): List<ParsedMoment>? = runCatching {
        val json = AiJsonParser.parseObject(content, knownKeys = listOf("moments"))
        json["moments"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            ParsedMoment(
                type = runCatching { MomentType.valueOf(obj.stringField("type").trim().uppercase()) }
                    .getOrDefault(MomentType.EMOTIONAL_PEAK),
                mood = runCatching { StoryMood.valueOf(obj.stringField("mood").trim().uppercase()) }
                    .getOrDefault(StoryMood.NEUTRAL),
                title = obj.stringField("title").trim(),
                summary = obj.stringField("summary").trim()
            ).takeIf { it.title.isNotBlank() && it.summary.isNotBlank() }
        }
    }.onFailure { e ->
        Log.e(TAG, "parseKeyMoments: malformed AI response, no moments extracted for this batch", e)
    }.getOrNull()

    private companion object {
        private const val SYSTEM_PROMPT = """
You detect significant story moments from a batch of roleplay messages worth remembering on a timeline.
Select moments that are narratively meaningful: first encounters, confessions, escalations, betrayals,
reveals, turning points, emotional shifts, promises, losses, or other scene-defining events — favor
moments that would matter to future continuity or memory, and skip mundane exchanges.
Respond with a single JSON object only, no markdown, of the exact shape:
{"moments": [{"type": "...", "mood": "...", "title": "...", "summary": "..."}]}
Include 0-3 moments. Each moment has:
- "type": one of FIRST_MEETING, CONFESSION, BREAKUP, NSFW_SCENE, PLOT_TWIST, EMOTIONAL_PEAK, CONFLICT, RECONCILIATION
- "mood": one of TENDER, ROMANTIC, DRAMATIC, HUMOROUS, DARK, TENSE, MELANCHOLIC, EXCITING, NEUTRAL
- "title": short title (max 60 chars)
- "summary": 2-3 sentence summary of what happened

If nothing significant happened, return {"moments": []} — this is a common and expected result, not a failure.
Write in the same language as the messages.
        """
    }
}