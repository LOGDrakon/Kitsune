package com.kitsune.core.memory.summarization

import android.util.Log
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.StoryChapterEntity
import com.kitsune.core.data.repository.StoryChapterRepository
import com.kitsune.core.network.json.AiJsonParser
import com.kitsune.core.network.json.AiJsonParser.stringField
import java.util.UUID
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.storyContentOnly
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MemoryFragmentRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.memory.lore.ExtractLoreEntriesUseCase
import com.kitsune.core.memory.lore.SyncCastFromLoreUseCase
import com.kitsune.core.memory.semantic.EmbeddingCache
import com.kitsune.core.memory.semantic.IndexMemoryFragmentUseCase
import com.kitsune.core.memory.timeline.ExtractKeyMomentsUseCase
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import javax.inject.Inject

private const val TAG = "UpdateChatSummaryUC"

/**
 * Level 2 of the memory pipeline: maintains a rolling narrative summary of everything older than the
 * raw message window. Also fires lore extraction (level 3) and semantic indexing (level 4) on the
 * same batch — but NO satellite use cases (objectives, journal, relationships, mood, projects,
 * contradictions, values, value-conflicts have been removed for simplicity and cost).
 *
 * Total LLM calls per trigger: 1 (summary) + 0-1 (chapter close, only past
 * [SummarizationConfig.META_SUMMARY_TRIGGER_CHARS]) + 1 (lore) + 1 (key moments) = 3-4.
 * Down from 10-11 in the previous architecture. The trigger itself fires ~3.6x less often than it
 * used to, since a batch is now [SummarizationConfig.SUMMARIZE_BATCH_MIN] messages rather than the
 * ~11 the old flat backlog gate produced (see the window/batch note in [SummarizationConfig]).
 *
 * **BUG-015 (BUGS.md) — deliberately NOT wrapped in a single DB transaction**: the summary write
 * below, and each of [extractLoreEntriesUseCase]/[syncCastFromLoreUseCase]/
 * [indexMemoryFragmentUseCase]/[extractKeyMomentsUseCase], are independent, individually
 * best-effort (`runCatching`) steps rather than one atomic unit. `extractLoreEntriesUseCase` and
 * `extractKeyMomentsUseCase` each make their own LLM network call before writing anything — a
 * single transaction spanning all of them would hold a write lock on the SQLCipher database open
 * across several slow, sequential network round-trips (seconds, sometimes tens of seconds),
 * blocking every other read/write against the same chat (including the message list the user is
 * actively looking at) for that whole duration. That trade would very likely be worse than the bug
 * it fixes. The accepted consequence: if one of these four steps fails after the summary itself
 * already saved successfully, that specific batch's lore entries/cast sync/semantic fragment/key
 * moments are silently skipped and never retried (the free-text summary already folded the batch
 * in, so the narrative itself is never lost — only the structured metadata derived from it). Each
 * failure is now at least logged (`Log.e`, previously swallowed with zero trace) so a real
 * occurrence is diagnosable; a proper "retry the lost batch later" mechanism would be a genuinely
 * separate feature, not attempted here.
 */
class UpdateChatSummaryUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver,
    private val extractLoreEntriesUseCase: ExtractLoreEntriesUseCase,
    private val syncCastFromLoreUseCase: SyncCastFromLoreUseCase,
    private val indexMemoryFragmentUseCase: IndexMemoryFragmentUseCase,
    private val extractKeyMomentsUseCase: ExtractKeyMomentsUseCase,
    private val memoryFragmentRepository: MemoryFragmentRepository,
    private val storyChapterRepository: StoryChapterRepository,
    private val embeddingCache: EmbeddingCache
) {
    suspend operator fun invoke(chatId: String) {
        val chat = chatRepository.getById(chatId) ?: return

        // BUG-104: the reserved window must match what the prompt actually sends verbatim
        // (SummarizationConfig.reservedWindow), and the trigger threshold must move with it —
        // window + batch, not a flat backlog count. A flat gate against a variable window is what
        // produced the double-counting (10 messages both verbatim and summarized), and naively
        // fixing only the dropLast would leave a large window with an empty batch and no memory
        // pipeline at all.
        val window = SummarizationConfig.reservedWindow(chat)
        val isFirstFold = chat.summary.isBlank() && chat.summarizedThroughCreatedAt == 0L
        val minBatch = if (isFirstFold) SummarizationConfig.FIRST_BATCH_MIN
                       else SummarizationConfig.SUMMARIZE_BATCH_MIN

        val toSummarize = messageRepository.getUnsummarized(chatId, chat.summarizedThroughCreatedAt)
        if (toSummarize.size < window + minBatch) return

        val batch = toSummarize.dropLast(window)
        if (batch.isEmpty()) return

        // BUG-015: the derived steps may be behind the summary, because a previous run's lore /
        // cast / index / key-moments step failed after the summary had already been written. Replay
        // from wherever they actually got to, not from where the summary got to — bounded by
        // DERIVED_MAX_RETRIES so a batch failing for a structural reason can't retry forever.
        val derivedBatch = if (chat.derivedThroughCreatedAt >= chat.summarizedThroughCreatedAt ||
                               chat.derivedRetryCount >= DERIVED_MAX_RETRIES) {
            batch
        } else {
            messageRepository.getUnsummarized(chatId, chat.derivedThroughCreatedAt).dropLast(window)
                .ifEmpty { batch }
        }

        val modelId = llmModelResolver.resolve(LlmOperation.SUMMARY)
        val existingSummary = chat.summary

        val userPrompt = buildString {
            if (existingSummary.isNotBlank()) {
                appendLine("Current summary:")
                appendLine(existingSummary)
                appendLine()
            }
            appendLine("New events to incorporate:")
            batch.storyContentOnly().forEach { message -> appendLine("${speakerLabel(message)}: ${message.content}") }
        }

        chatCompletionRepository.complete(
            modelId = modelId,
            systemPrompt = SUMMARIZATION_SYSTEM_PROMPT.trim(),
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = userPrompt)),
            operationType = "SUMMARY",
            maxTokens = ChatCompletionRepository.SUMMARY_MAX_TOKENS
        ).onSuccess { result ->
            val foldedSummary = result.content.trim()
            val rolledOver = closeChapterIfNeeded(chat, foldedSummary, batch, modelId)

            chatRepository.upsert(
                chat.copy(
                    summary = rolledOver,
                    summarizedThroughCreatedAt = batch.last().createdAt,
                    updatedAt = System.currentTimeMillis()
                )
            )

            var allDerivedSucceeded = true

            // Level 3: Lore extraction
            runCatching { extractLoreEntriesUseCase(chatId, derivedBatch) }
                .onFailure { e ->
                    allDerivedSucceeded = false
                    Log.e(TAG, "invoke: lore extraction failed for chatId=$chatId, batch ending ${derivedBatch.last().createdAt} — will be retried", e)
                }

            // Ensemble cast sync from lore entries
            runCatching { syncCastFromLoreUseCase(chatId) }
                .onFailure { e ->
                    allDerivedSucceeded = false
                    Log.e(TAG, "invoke: cast sync failed for chatId=$chatId — will be retried", e)
                }

            // Level 4: Semantic index of the batch
            val batchText = derivedBatch.storyContentOnly().joinToString("\n") { "${speakerLabel(it)}: ${it.content}" }
            val sourceKey = "batch-${derivedBatch.last().createdAt}"
            runCatching {
                indexMemoryFragmentUseCase(chatId, MemoryFragmentSource.SUMMARY_BATCH, sourceKey, batchText)
                pruneSupersededChunks(chatId, derivedBatch.first().createdAt, derivedBatch.last().createdAt)
            }.onFailure { e ->
                allDerivedSucceeded = false
                Log.e(TAG, "invoke: semantic indexing failed for chatId=$chatId, sourceKey=$sourceKey — will be retried", e)
            }

            // Timeline: extract key moments from the batch
            runCatching { extractKeyMomentsUseCase(chatId, derivedBatch) }
                .onFailure { e ->
                    allDerivedSucceeded = false
                    Log.e(TAG, "invoke: key moment extraction failed for chatId=$chatId, batch ending ${derivedBatch.last().createdAt} — will be retried", e)
                }

            advanceDerivedCursor(chatId, derivedBatch.last().createdAt, allDerivedSucceeded)
        }
    }

    /**
     * Deletes the [MemoryFragmentSource.MESSAGE_CHUNK] fragments covering messages that the batch
     * fragment written just above now covers in full.
     *
     * No information is lost: a batch fragment holds the raw text of the same messages, in the same
     * speaker-labelled form, only over a wider span. Left in place, the eight-message chunks and the
     * batch that subsumes them compete for the same top-K slots with near-identical text, so a
     * single remembered scene can crowd out every other memory. Dropping them also roughly halves
     * the fragment count, which compounds with [com.kitsune.core.memory.semantic.EmbeddingCache].
     */
    private suspend fun pruneSupersededChunks(chatId: String, fromCreatedAt: Long, throughCreatedAt: Long) {
        memoryFragmentRepository.getByChat(chatId)
            .filter { it.sourceType == MemoryFragmentSource.MESSAGE_CHUNK }
            .filter { fragment ->
                val timestamp = fragment.sourceKey.removePrefix("chunk-").toLongOrNull()
                timestamp != null && timestamp in fromCreatedAt..throughCreatedAt
            }
            .forEach {
                memoryFragmentRepository.delete(it)
                embeddingCache.invalidate(it.id)
            }
    }

    /**
     * Freezes the current rolling summary into a [StoryChapterEntity] once it outgrows
     * [SummarizationConfig.META_SUMMARY_TRIGGER_CHARS], and returns the summary the chat should
     * carry forward (empty after a successful close, since a fresh chapter starts).
     *
     * This **replaces** the old `compactIfNeeded`, at exactly the same trigger, on the same model,
     * with the same token budget and the same single call — the cost is unchanged. What changes is
     * the semantics: compaction used to re-compress the whole summary onto itself at every
     * threshold crossing, so the opening of a long story was paraphrased again and again, losing a
     * little each time (IDEAS.md, open since 2026-07-03). A chapter is compressed once and then
     * never rewritten.
     *
     * Degrades rather than loses: if the call fails or the response can't be parsed, the raw text
     * becomes the chapter body and its opening becomes the title.
     */
    private suspend fun closeChapterIfNeeded(
        chat: ChatEntity,
        summary: String,
        batch: List<MessageEntity>,
        modelId: String
    ): String {
        if (summary.length <= SummarizationConfig.META_SUMMARY_TRIGGER_CHARS) return summary

        val closed = runCatching {
            val raw = chatCompletionRepository.complete(
                modelId = modelId,
                systemPrompt = CHAPTER_CLOSE_SYSTEM_PROMPT.trim(),
                messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = summary)),
                operationType = "SUMMARY",
                maxTokens = ChatCompletionRepository.MEMORY_MAX_TOKENS
            ).getOrThrow().content

            val json = AiJsonParser.parseObject(raw, knownKeys = listOf("title", "summary"))
            val title = json.stringField("title").trim()
            val body = json.stringField("summary").trim()
            if (body.isBlank()) throw IllegalStateException("chapter close returned no summary")
            title.ifBlank { body.take(CHAPTER_TITLE_FALLBACK_CHARS) } to body
        }.getOrElse { e ->
            Log.e(TAG, "closeChapterIfNeeded: falling back to the uncompacted summary for chatId=${chat.id}", e)
            summary.take(CHAPTER_TITLE_FALLBACK_CHARS) to summary
        }

        val (title, body) = closed
        val chapterIndex = storyChapterRepository.getMaxChapterIndex(chat.id) + 1
        val chapterId = UUID.randomUUID().toString()

        return runCatching {
            storyChapterRepository.upsert(
                StoryChapterEntity(
                    id = chapterId,
                    chatId = chat.id,
                    chapterIndex = chapterIndex,
                    title = title,
                    summary = body,
                    fromCreatedAt = chat.summarizedThroughCreatedAt,
                    throughCreatedAt = batch.last().createdAt,
                    createdAt = System.currentTimeMillis()
                )
            )
            // Indexed so chapters pushed past the prompt's chapter cap stay reachable via retrieval.
            runCatching {
                indexMemoryFragmentUseCase(
                    chat.id,
                    MemoryFragmentSource.CHAPTER_SUMMARY,
                    chapterId,
                    "$title\n$body"
                )
            }.onFailure { e -> Log.e(TAG, "closeChapterIfNeeded: indexing chapter $chapterIndex failed for chatId=${chat.id}", e) }
            ""
        }.getOrElse { e ->
            // The chapter row could not be written — keep the summary in the chat rather than
            // clearing it, or the story so far would simply vanish.
            Log.e(TAG, "closeChapterIfNeeded: could not persist chapter for chatId=${chat.id}, keeping the rolling summary", e)
            summary
        }
    }

    /**
     * Advances the derived-step cursor only when every derived step succeeded (BUG-015). On failure
     * the cursor stays put so the next trigger replays the same batch, and a retry counter bounds
     * how often that can happen — a batch that fails structurally (a message the classifier always
     * rejects, say) must not re-run four API calls on every send forever.
     */
    private suspend fun advanceDerivedCursor(chatId: String, throughCreatedAt: Long, succeeded: Boolean) {
        runCatching {
            val current = chatRepository.getById(chatId) ?: return@runCatching
            val updated = when {
                succeeded -> current.copy(derivedThroughCreatedAt = throughCreatedAt, derivedRetryCount = 0)
                current.derivedRetryCount + 1 >= DERIVED_MAX_RETRIES -> {
                    Log.e(TAG, "advanceDerivedCursor: giving up on the batch ending $throughCreatedAt for chatId=$chatId after $DERIVED_MAX_RETRIES attempts")
                    current.copy(derivedThroughCreatedAt = throughCreatedAt, derivedRetryCount = 0)
                }
                else -> current.copy(derivedRetryCount = current.derivedRetryCount + 1)
            }
            chatRepository.upsert(updated.copy(updatedAt = System.currentTimeMillis()))
        }.onFailure { e -> Log.e(TAG, "advanceDerivedCursor: failed for chatId=$chatId", e) }
    }

    private fun speakerLabel(message: MessageEntity): String = when (message.role) {
        MessageRole.USER -> "User"
        MessageRole.ASSISTANT -> "Character"
        MessageRole.SYSTEM -> "System"
        // Filtered out upstream by storyContentOnly(); labelled rather than crashed on if one ever slips through.
        MessageRole.STYLE_DIRECTIVE -> "Style directive"
    }

    companion object {
        private val SUMMARIZATION_SYSTEM_PROMPT = """
You are a story-memory assistant for an adult roleplay application.
Your job is to maintain a running summary of the story so far.
Fold the new events into the existing summary (if any), preserving all important facts: character
developments, plot points, emotional shifts, locations visited, relationships established, promises,
conflicts, reveals, items acquired, and unresolved threads.
Do not compress or shorten parts of the existing summary that are still relevant just to save space —
your job right now is completeness, not brevity. It is fine, expected even, for the summary to grow
longer as the story does; a separate compaction pass handles trimming once it gets too long, so do not
pre-emptively summarize-the-summary here. Prefer specific, reusable facts over vague prose, and never
drop a detail from the existing summary without a good reason (it became false, or a strictly more
specific new fact replaced it).
The summary should remain easy for a model to use later as story context: coherent and rich in
continuity, not necessarily short.
Write in the same language as the messages.
""".trimIndent()

        private val CHAPTER_CLOSE_SYSTEM_PROMPT = """
You are closing a chapter of an ongoing roleplay story.
The text below is the running summary of everything that has happened since the previous chapter ended.
Turn it into a finished, self-contained chapter record.
Respond with a single JSON object only, no markdown:
{"title": "...", "summary": "..."}
"title": a short evocative chapter title (max 60 characters), in the story's language, naming what this
stretch of story was actually about — not "Chapter 4", not a generic label.
"summary": a compact but complete account of this chapter, under 2000 characters. Keep every fact a later
chapter could depend on: names, relationships and how they changed, promises made, injuries, objects
acquired or lost, locations, reveals, and unresolved tensions. Remove repetition and scene-level texture,
never meaning. Write it as narrative past tense, in the same language as the text below.
""".trimIndent()

        /** Bounded replay of a batch whose derived steps failed, before giving up and moving on. */
        private const val DERIVED_MAX_RETRIES = 3

        /** Length of the summary excerpt used as a chapter title when the model gave none. */
        private const val CHAPTER_TITLE_FALLBACK_CHARS = 60
    }
}
