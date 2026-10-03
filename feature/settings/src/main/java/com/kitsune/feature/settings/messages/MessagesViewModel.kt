package com.kitsune.feature.settings.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.UserMessageManager
import com.kitsune.core.backend.model.UserMessageResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Mes messages" (Settings) — full history of one-way moderation messages sent to this user, see
 * [UserMessageManager]. */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val userMessageManager: UserMessageManager
) : ViewModel() {

    val messages: StateFlow<List<UserMessageResponse>> = userMessageManager.messages

    init {
        viewModelScope.launch { userMessageManager.refresh() }
    }

    fun markRead(messageId: String) {
        viewModelScope.launch { userMessageManager.markRead(messageId) }
    }
}
