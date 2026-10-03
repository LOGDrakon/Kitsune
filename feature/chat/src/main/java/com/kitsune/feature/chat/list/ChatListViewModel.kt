package com.kitsune.feature.chat.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatWithLastMessage(
    val chat: ChatEntity,
    val lastMessage: MessageEntity? = null
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val personaId: String = checkNotNull(savedStateHandle["personaId"])

    private val _persona = MutableStateFlow<PersonaEntity?>(null)
    val persona: StateFlow<PersonaEntity?> = _persona.asStateFlow()

    val chats: StateFlow<List<ChatEntity>> = chatRepository.observeByPersona(personaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _chatsWithMessages = MutableStateFlow<List<ChatWithLastMessage>>(emptyList())
    val chatsWithMessages: StateFlow<List<ChatWithLastMessage>> = _chatsWithMessages.asStateFlow()

    private val _openChatEvents = MutableSharedFlow<String>()
    val openChatEvents: SharedFlow<String> = _openChatEvents

    init {
        viewModelScope.launch { _persona.value = personaRepository.getById(personaId) }
        viewModelScope.launch {
            chats.collect { chatList ->
                loadLastMessages(chatList)
            }
        }
    }

    private suspend fun loadLastMessages(chatList: List<ChatEntity>) {
        val updated = chatList.map { chat ->
            val lastMsg = messageRepository.getLastMessage(chat.id)
            ChatWithLastMessage(chat, lastMsg)
        }
        _chatsWithMessages.value = updated
    }

    fun startNewChat() {
        viewModelScope.launch {
            val chat = chatRepository.createChat(personaId, title = "")
            _openChatEvents.emit(chat.id)
        }
    }
    
    fun deleteChat(chat: ChatEntity) {
        viewModelScope.launch {
            runCatching { chatRepository.deleteChatWithMessages(chat) }
        }
    }
}
