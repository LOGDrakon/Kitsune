package com.kitsune.feature.chat.list

import com.kitsune.core.designsystem.component.KitsunePage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.designsystem.SwipeToDeleteRow
import com.kitsune.feature.chat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (chatId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val persona by viewModel.persona.collectAsStateWithLifecycle()
    val chatsWithMessages by viewModel.chatsWithMessages.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.openChatEvents.collect { chatId -> onOpenChat(chatId) }
    }

    KitsunePage(
        title = persona?.name.orEmpty(),
        condensedTitle = true,
        onBack = onBack,
        floatingAction = {
            FloatingActionButton(onClick = viewModel::startNewChat) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_conversation_label))
            }
        }
    ) { padding ->
        if (chatsWithMessages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        stringResource(R.string.chat_list_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        stringResource(R.string.chat_list_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(chatsWithMessages, key = { it.chat.id }) { item ->
                    SwipeToDeleteChatItem(
                        chatWithLastMessage = item,
                        onOpenChat = onOpenChat,
                        onDeleteChat = { viewModel.deleteChat(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteChatItem(
    chatWithLastMessage: ChatWithLastMessage,
    onOpenChat: (String) -> Unit,
    onDeleteChat: (ChatEntity) -> Unit
) {
    SwipeToDeleteRow(
        onDelete = { onDeleteChat(chatWithLastMessage.chat) },
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        val lastMsg = chatWithLastMessage.lastMessage
        val context = LocalContext.current
        val subtitle = when {
            lastMsg != null && lastMsg.role == MessageRole.SYSTEM -> lastMsg.content
            lastMsg != null -> lastMsg.content
            else -> stringResource(R.string.new_conversation_label)
        }
        val timestamp = lastMsg?.let { formatRelativeTime(it.createdAt, context) }
            ?: formatRelativeTime(chatWithLastMessage.chat.updatedAt, context)

        ListItem(
            headlineContent = {
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = { Text(timestamp) },
            modifier = Modifier
                .clickable { onOpenChat(chatWithLastMessage.chat.id) }
                .fillMaxWidth()
        )
    }
}
