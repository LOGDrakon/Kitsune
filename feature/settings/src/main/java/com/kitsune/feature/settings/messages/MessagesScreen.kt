package com.kitsune.feature.settings.messages

import com.kitsune.core.designsystem.component.KitsunePage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.backend.model.UserMessageResponse
import com.kitsune.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var expandedId by remember { mutableStateOf<String?>(null) }

    KitsunePage(
        title = stringResource(R.string.messages_title),
        condensedTitle = true,
        onBack = onBack
    ) { padding ->
        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.messages_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = UserMessageResponse::id) { message ->
                    MessageCard(
                        message = message,
                        expanded = expandedId == message.id,
                        onClick = {
                            expandedId = if (expandedId == message.id) null else message.id
                            if (message.readAt == null) viewModel.markRead(message.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: UserMessageResponse, expanded: Boolean, onClick: () -> Unit) {
    val isUnread = message.readAt == null
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isUnread) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = stringResource(R.string.messages_unread_content_description),
                        modifier = Modifier.size(8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    message.subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message.createdAt.take(10),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
