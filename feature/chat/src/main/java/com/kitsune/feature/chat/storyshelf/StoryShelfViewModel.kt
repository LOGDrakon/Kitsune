package com.kitsune.feature.chat.storyshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.data.repository.StoryChapterRepository
import com.kitsune.core.security.storage.EncryptedImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The shelf's data.
 *
 * Deliberately thin: everything that could be got wrong — the badge, the order, the title fallback —
 * lives in `StoryShelf.kt` as pure functions with tests, because this module has no ViewModel test
 * and never will. What is left here is loading and image decryption.
 */
@HiltViewModel
class StoryShelfViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val storyChapterRepository: StoryChapterRepository,
    private val encryptedImageStore: EncryptedImageStore
) : ViewModel() {

    /** Chapter titles and counts, loaded per story and cached — one read per chat, not one per frame. */
    private val _chaptersByChat = MutableStateFlow<Map<String, Pair<String?, Int>>>(emptyMap())

    val stories: StateFlow<List<ShelfStory>> = combine(
        chatRepository.observeActive(),
        personaRepository.observeAll(),
        _chaptersByChat
    ) { chats, personas, chapters ->
        val personaById = personas.associateBy { it.id }
        shelfOrder(
            chats.map { chat ->
                val persona = chat.personaId?.let(personaById::get)
                val (firstChapterTitle, chapterCount) = chapters[chat.id] ?: (null to 0)
                ShelfStory(
                    chatId = chat.id,
                    title = shelfTitle(chat, firstChapterTitle, persona?.name, fallbackTitle),
                    coverImageId = chat.coverImageId,
                    avatarImageId = persona?.avatarImageId,
                    chapterCount = chapterCount,
                    isEnsemble = chat.personaId == null,
                    hasUnseenDevelopment = hasUnseenDevelopment(chat),
                    updatedAt = chat.updatedAt
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set by the screen from string resources — `core:data` types must not reach for Android ones. */
    var fallbackTitle: String = "Histoire"

    private val _imageBytesById = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val imageBytesById: StateFlow<Map<String, ByteArray>> = _imageBytesById.asStateFlow()

    /** Loads chapter data for stories that do not have it yet. Safe to call on every recomposition. */
    fun ensureChaptersLoaded(chatIds: List<String>) {
        val missing = chatIds.filter { it !in _chaptersByChat.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = missing.associateWith { id ->
                val chapters = runCatching { storyChapterRepository.getByChat(id) }.getOrDefault(emptyList())
                chapters.firstOrNull()?.title to chapters.size
            }
            _chaptersByChat.value = _chaptersByChat.value + loaded
        }
    }

    /** Decrypts covers and portraits off the main thread, once each. */
    fun ensureImagesLoaded(stories: List<ShelfStory>) {
        val missing = stories
            .flatMap { listOfNotNull(it.coverImageId, it.avatarImageId) }
            .filter { it !in _imageBytesById.value }
            .distinct()
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                missing.mapNotNull { id -> encryptedImageStore.load(id)?.let { id to it } }
            }
            _imageBytesById.value = _imageBytesById.value + loaded
        }
    }

    fun deleteStory(chatId: String) {
        viewModelScope.launch {
            val chat: ChatEntity = chatRepository.getById(chatId) ?: return@launch
            runCatching { chatRepository.deleteChatWithMessages(chat) }
        }
    }
}
