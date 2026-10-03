package com.kitsune.feature.chat.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.feature.chat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryMemoryScreen(
    onBack: () -> Unit,
    viewModel: StoryMemoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.story_memory_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is StoryMemoryUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is StoryMemoryUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = viewModel::dismissError) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
            is StoryMemoryUiState.Ready -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SummarySection(summary = state.chat.summary)
                    }

                    item {
                        Text(
                            stringResource(R.string.story_memory_lore_count_label, state.loreEntries.size),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (state.loreEntries.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.story_memory_no_lore_entries),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(state.loreEntries, key = { it.id }) { entry ->
                            LoreEntryCard(
                                entry = entry,
                                onEdit = { viewModel.startEditingLoreEntry(entry) },
                                onDelete = { viewModel.deleteLoreEntry(entry) }
                            )
                        }
                    }
                }

                state.editingLoreEntry?.let { entry ->
                    EditLoreEntryDialog(
                        entry = entry,
                        onSave = viewModel::saveLoreEntry,
                        onDismiss = viewModel::cancelEditing
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarySection(summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.story_memory_summary_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    Icons.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (summary.isBlank()) {
                Text(
                    stringResource(R.string.story_memory_no_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun LoreEntryCard(
    entry: LoreEntryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        when (entry.entryType) {
                            LoreEntryType.CHARACTER -> Icons.Default.Person
                            LoreEntryType.LOCATION -> Icons.Default.Place
                            LoreEntryType.FACTION -> Icons.Default.Groups
                            LoreEntryType.EVENT -> Icons.Default.Event
                            LoreEntryType.ITEM -> Icons.Default.Inventory
                            // Un fil narratif est une question laissée ouverte.
                            LoreEntryType.THREAD -> Icons.Default.QuestionMark
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_edit),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                entry.entryType.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (entry.entryType == LoreEntryType.EVENT && entry.occurredAt.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    entry.occurredAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                entry.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.isAutoGenerated) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.story_memory_auto_generated_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.story_memory_delete_entry_title)) },
            text = { Text(stringResource(R.string.story_memory_delete_entry_text, entry.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLoreEntryDialog(
    entry: LoreEntryEntity,
    onSave: (name: String, summary: String, content: String, occurredAt: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(entry.id) { mutableStateOf(entry.name) }
    var summary by remember(entry.id) { mutableStateOf(entry.summary) }
    var content by remember(entry.id) { mutableStateOf(entry.content) }
    var occurredAt by remember(entry.id) { mutableStateOf(entry.occurredAt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.story_memory_edit_entry_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.story_memory_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringResource(R.string.story_memory_summary_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.story_memory_content_label)) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    maxLines = 10
                )
                if (entry.entryType == LoreEntryType.EVENT) {
                    OutlinedTextField(
                        value = occurredAt,
                        onValueChange = { occurredAt = it },
                        label = { Text(stringResource(R.string.story_memory_occurred_at_label)) },
                        supportingText = { Text(stringResource(R.string.story_memory_occurred_at_helper)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, summary, content, occurredAt) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
