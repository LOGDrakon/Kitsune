package com.kitsune.feature.chat.branchtree

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatBranchTreeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val buildChatBranchTreeUseCase: BuildChatBranchTreeUseCase,
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository
) : ViewModel() {

    /** The chat the tree was opened from — highlighted in the tree, never itself clickable. */
    val currentChatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _roots = MutableStateFlow<List<BranchTreeNode>>(emptyList())
    val roots: StateFlow<List<BranchTreeNode>> = _roots.asStateFlow()

    /** Fallback label for a chat whose own `title` is blank — every never-forked persona chat
     * starts out this way ([com.kitsune.feature.chat.list.ChatListViewModel.startNewChat] creates
     * it with `title = ""`), and would otherwise render as an empty row in the tree. Null for an
     * ensemble/universe family, where `chat.title` is always meaningfully set at creation. */
    private val _personaNameFallback = MutableStateFlow<String?>(null)
    val personaNameFallback: StateFlow<String?> = _personaNameFallback.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _roots.value = buildChatBranchTreeUseCase(currentChatId)
            val personaId = chatRepository.getById(currentChatId)?.personaId
            _personaNameFallback.value = personaId?.let { personaRepository.getById(it)?.name }
            _isLoading.value = false
        }
    }
}
