package com.kitsune.feature.chat.novel

import android.util.Log
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.repository.ChatParticipantRepository
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.data.repository.StoryChapterRepository
import com.kitsune.core.security.storage.EncryptedImageStore
import javax.inject.Inject

private const val TAG = "EnsureStoryCoverUC"

/**
 * Gives a story its cover, once it has earned one (2026-08-25).
 *
 * ## Why this exists
 *
 * `GenerateNovelCoverUseCase` has always been able to draw a cover from a story's title, its rolling
 * summary and its cast's visual sheets. But it only ever ran **inside the PDF export**, and the
 * bitmap was decoded into the document and dropped — the app generated a book cover for every
 * exported story and then had none to show anywhere. That is the missing piece under the whole
 * "shelf of stories rather than list of chats" idea: a shelf needs spines.
 *
 * ## Why at the first closed chapter, and not at creation
 *
 * A cover drawn from an empty story is a cover of nothing. The first `StoryChapterEntity` is the
 * earliest point where the story has stated what it is about — that is precisely what chapter closing
 * produces, a title plus a frozen summary. Waiting also means we never spend a generation on the
 * conversations people open once and abandon, which is most of them.
 *
 * ## Cost
 *
 * `NOVEL_COVER` is in the backend's `FREE_OPERATION_TYPES`, so this costs the user nothing. It is
 * still one real image generation against the provider, which is why it runs **once per story** and
 * is guarded by [ChatEntity.coverImageId] rather than by a flag that could be reset.
 *
 * Self-healing by construction: it is safe to call on every chat open, and it will quietly give a
 * cover to stories that predate the field.
 */
class EnsureStoryCoverUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val storyChapterRepository: StoryChapterRepository,
    private val personaRepository: PersonaRepository,
    private val npcRepository: NpcRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val generateNovelCoverUseCase: GenerateNovelCoverUseCase,
    private val encryptedImageStore: EncryptedImageStore
) {
    /** @return the cover's store id when one was created, null when nothing was to be done. */
    suspend operator fun invoke(chatId: String): String? {
        val chat = chatRepository.getById(chatId) ?: return null
        if (chat.coverImageId != null) return null

        val chapters = storyChapterRepository.getByChat(chatId)
        if (chapters.isEmpty()) return null

        // The chapter's own title is the story's title until the user renames it: the chaptering pass
        // already asks the model for an evocative one, and until now it was written to the database
        // and displayed nowhere.
        val title = chat.title.ifBlank { chapters.first().title }.ifBlank { return null }

        val persona = chat.personaId?.let { personaRepository.getById(it) }
        val npcs = chatParticipantRepository.getByChat(chatId)
            .mapNotNull { npcRepository.getById(it.participantId) }

        val characterContext = buildString {
            persona?.let {
                appendLine(it.name)
                if (it.shortDescription.isNotBlank()) appendLine(it.shortDescription)
            }
            npcs.forEach {
                appendLine(it.name)
                if (it.description.isNotBlank()) appendLine(it.description)
            }
        }.trim()

        val allowMature = persona?.maturityTags.orEmpty()
            .any { it == MaturityTag.NSFW || it == MaturityTag.DARK }

        // A story without a cover is a smaller loss than a story that will not open, so every failure
        // here is swallowed: the shelf falls back to a typographic spine and tries again next time.
        val bytes = runCatching {
            generateNovelCoverUseCase(
                title = title,
                storySummary = chapters.joinToString("\n\n") { it.summary }.ifBlank { chat.summary },
                characterContext = characterContext,
                allowMatureContent = allowMature
            )
        }.getOrElse { e ->
            Log.w(TAG, "cover generation failed for $chatId: ${e::class.simpleName}: ${e.message}")
            null
        } ?: return null

        val storeId = runCatching { encryptedImageStore.save(bytes) }.getOrElse { e ->
            Log.e(TAG, "cover generated but could not be stored for $chatId", e)
            return null
        }

        // Re-read rather than reusing the snapshot above: generating an image takes seconds, and the
        // summarization pipeline may well have written to this row in the meantime.
        val fresh = chatRepository.getById(chatId) ?: return null
        if (fresh.coverImageId != null) return null
        chatRepository.upsert(
            fresh.copy(
                coverImageId = storeId,
                title = fresh.title.ifBlank { title },
                updatedAt = fresh.updatedAt
            )
        )
        Log.d(TAG, "cover stored for $chatId")
        return storeId
    }
}
