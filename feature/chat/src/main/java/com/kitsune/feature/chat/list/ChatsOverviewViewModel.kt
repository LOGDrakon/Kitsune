package com.kitsune.feature.chat.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.PersonaRepository
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

data class ChatWithPersona(
    val chat: ChatEntity,
    val persona: PersonaEntity?,
    val lastMessage: MessageEntity? = null
)

@HiltViewModel
class ChatsOverviewViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val messageRepository: MessageRepository,
    private val encryptedImageStore: EncryptedImageStore
) : ViewModel() {

    val chats: StateFlow<List<ChatWithPersona>> = combine(
        chatRepository.observeActive(),
        personaRepository.observeAll()
    ) { chats, personas ->
        val personaById = personas.associateBy { it.id }
        chats.map { ChatWithPersona(it, it.personaId?.let(personaById::get)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _avatarBytesById = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val avatarBytesById: StateFlow<Map<String, ByteArray>> = _avatarBytesById.asStateFlow()

    fun ensureAvatarsLoaded(items: List<ChatWithPersona>) {
        val missingIds = items.mapNotNull { it.persona?.avatarImageId }.filter { it !in _avatarBytesById.value }
        if (missingIds.isEmpty()) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                missingIds.mapNotNull { id -> encryptedImageStore.load(id)?.let { id to it } }
            }
            _avatarBytesById.value = _avatarBytesById.value + loaded
        }
    }

    fun loadLastMessages(items: List<ChatWithPersona>) {
        viewModelScope.launch {
            val updated = items.map { item ->
                val lastMsg = messageRepository.getLastMessage(item.chat.id)
                item.copy(lastMessage = lastMsg)
            }
            _chatsWithMessages.value = updated
        }
    }

    private val _chatsWithMessages = MutableStateFlow<List<ChatWithPersona>>(emptyList())
    val chatsWithMessages: StateFlow<List<ChatWithPersona>> = _chatsWithMessages.asStateFlow()

    fun deleteChat(chat: ChatEntity) {
        viewModelScope.launch {
            runCatching { chatRepository.deleteChatWithMessages(chat) }
        }
    }
}
