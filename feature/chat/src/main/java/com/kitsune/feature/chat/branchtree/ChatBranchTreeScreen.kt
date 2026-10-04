package com.kitsune.feature.chat.branchtree

import com.kitsune.core.designsystem.component.KitsunePage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.feature.chat.R
import com.kitsune.feature.chat.list.formatRelativeTime

private val CONNECTOR_WIDTH = 24.dp
private val CONNECTOR_ROW_HEIGHT = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBranchTreeScreen(
    onBack: () -> Unit,
    onOpenChat: (chatId: String) -> Unit,
    viewModel: ChatBranchTreeViewModel = hiltViewModel()
) {
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val personaNameFallback by viewModel.personaNameFallback.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    KitsunePage(
        title = stringResource(R.string.chat_branch_tree_title),
        condensedTitle = true,
        onBack = onBack
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                roots.isEmpty() -> Text(
                    stringResource(R.string.chat_branch_tree_empty),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                    roots.forEach { root ->
                        branchNodeItems(
                            node = root,
                            depth = 0,
                            currentChatId = viewModel.currentChatId,
                            personaNameFallback = personaNameFallback,
                            onOpenChat = onOpenChat
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.branchNodeItems(
    node: BranchTreeNode,
    depth: Int,
    currentChatId: String,
    personaNameFallback: String?,
    onOpenChat: (chatId: String) -> Unit
) {
    item(key = node.chat.id) {
        BranchNodeRow(
            node = node,
            depth = depth,
            isCurrent = node.chat.id == currentChatId,
            personaNameFallback = personaNameFallback,
            onOpenChat = onOpenChat
        )
    }
    node.children.forEach { child ->
        branchNodeItems(child, depth + 1, currentChatId, personaNameFallback, onOpenChat)
    }
}

@Composable
private fun BranchNodeRow(
    node: BranchTreeNode,
    depth: Int,
    isCurrent: Boolean,
    personaNameFallback: String?,
    onOpenChat: (chatId: String) -> Unit
) {
    val context = LocalContext.current
    val label = node.chat.title.trim().ifEmpty { personaNameFallback ?: stringResource(R.string.new_conversation_label) }
    val connectorColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = CONNECTOR_WIDTH * depth)
    ) {
        if (depth > 0) {
            Canvas(modifier = Modifier.size(width = CONNECTOR_WIDTH, height = CONNECTOR_ROW_HEIGHT)) {
                val midY = size.height / 2f
                val midX = size.width / 2f
                drawLine(connectorColor, start = Offset(midX, 0f), end = Offset(midX, midY), strokeWidth = 2.dp.toPx())
                drawLine(connectorColor, start = Offset(midX, midY), end = Offset(size.width, midY), strokeWidth = 2.dp.toPx())
            }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .then(if (isCurrent) Modifier else Modifier.clickable { onOpenChat(node.chat.id) })
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrent) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                stringResource(R.string.chat_branch_tree_current_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    formatRelativeTime(node.chat.updatedAt, context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
