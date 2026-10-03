package com.kitsune.feature.chat.list

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.designsystem.DecryptedImage
import com.kitsune.core.designsystem.LocalDiscreetMode
import com.kitsune.core.designsystem.SwipeToDeleteRow
import com.kitsune.core.designsystem.discreetBlur
import com.kitsune.feature.chat.R

@Composable
fun ChatsOverviewScreen(
    onOpenChat: (chatId: String) -> Unit,
    viewModel: ChatsOverviewViewModel = hiltViewModel()
) {
    val chats by viewModel.chatsWithMessages.collectAsStateWithLifecycle()
    val avatarBytesById by viewModel.avatarBytesById.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.chats.collect { items ->
            viewModel.loadLastMessages(items)
            viewModel.ensureAvatarsLoaded(items)
        }
    }

    if (chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    stringResource(R.string.chat_overview_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(chats, key = { it.chat.id }) { item ->
                SwipeToDeleteRow(
                    onDelete = { viewModel.deleteChat(item.chat) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    val lastMsg = item.lastMessage
                    val context = LocalContext.current
                    val subtitle = when {
                        lastMsg != null && lastMsg.role == MessageRole.SYSTEM -> lastMsg.content
                        lastMsg != null -> lastMsg.content
                        else -> stringResource(R.string.new_conversation_label)
                    }
                    val timestamp = lastMsg?.let { formatRelativeTime(it.createdAt, context) }
                        ?: formatRelativeTime(item.chat.updatedAt, context)

                    // A chat's personaId is null by design for ensemble/universe chats (no single
                    // protagonist, see FEATURES.md section 6) — not a sign the persona was deleted.
                    // Deleting a persona actually CASCADE-deletes its chats entirely (ChatEntity's
                    // FK), so a real persona chat can never legitimately end up with a null lookup.
                    val isEnsembleChat = item.chat.personaId == null
                    val ensembleFallback = stringResource(R.string.chat_overview_ensemble_chat_fallback_title)
                    ListItem(
                        headlineContent = {
                            Text(if (isEnsembleChat) item.chat.title.ifBlank { ensembleFallback } else item.persona?.name.orEmpty())
                        },
                        supportingContent = {
                            Text(
                                "$subtitle · $timestamp",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            val bytes = item.persona?.avatarImageId?.let { avatarBytesById[it] }
                            val isDiscreet = LocalDiscreetMode.current
                            Box(modifier = Modifier.size(48.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                if (bytes != null) {
                                    DecryptedImage(
                                        bytes = bytes,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).then(
                                            if (isDiscreet) Modifier.discreetBlur(12.dp) else Modifier
                                        ),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(if (isEnsembleChat) Icons.Default.Groups else Icons.Default.Person, contentDescription = null)
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable { onOpenChat(item.chat.id) }
                    )
                }
            }
        }
    }
}

fun formatRelativeTime(millis: Long, context: Context): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000 -> context.getString(R.string.time_just_now)
        diff < 3_600_000 -> context.getString(R.string.time_minutes_ago, diff / 60_000)
        diff < 86_400_000 -> context.getString(R.string.time_hours_ago, diff / 3_600_000)
        diff < 604_800_000 -> context.getString(R.string.time_days_ago, diff / 86_400_000)
        diff < 2_592_000_000 -> context.getString(R.string.time_weeks_ago, diff / 604_800_000)
        else -> context.getString(R.string.time_months_ago, diff / 2_592_000_000)
    }
}
