package com.kitsune.feature.universe.chatcreate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.feature.universe.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniverseChatCreationScreen(
    onCreated: (chatId: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: UniverseChatCreationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.universe_chat_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    ) { padding ->
        when (val current = state) {
            is UniverseChatCreationUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UniverseChatCreationUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        stringResource(R.string.universe_chat_create_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = current.title,
                        onValueChange = viewModel::updateTitle,
                        label = { Text(stringResource(R.string.universe_chat_create_title_label)) },
                        placeholder = { Text(stringResource(R.string.universe_chat_create_title_placeholder)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )

                    Text(
                        stringResource(R.string.universe_chat_create_characters_count_format, current.selectedIds.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )

                    if (current.candidates.isEmpty()) {
                        Text(
                            stringResource(R.string.universe_chat_create_empty_candidates),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        current.candidates.forEach { candidate ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                                    .clickable { viewModel.toggleSelection(candidate.id) },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = candidate.id in current.selectedIds,
                                        onCheckedChange = { viewModel.toggleSelection(candidate.id) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(candidate.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            candidate.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        if (candidate.type == ParticipantType.PERSONA) stringResource(R.string.universe_chat_create_type_persona) else stringResource(R.string.universe_chat_create_type_npc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    current.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    Button(
                        onClick = { viewModel.save(onCreated) },
                        enabled = !current.isSaving,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        if (current.isSaving) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.universe_chat_create_submit))
                    }
                }
            }
        }
    }
}
