package com.kitsune.feature.auth.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.feature.auth.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoyNoteEditorScreen(
    onBack: () -> Unit,
    viewModel: DecoyNoteEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val ready = state as? NoteEditorUiState.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.decoy_content_desc_back))
                    }
                },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { viewModel.save(ready.title, ready.body, !ready.isPinned) }) {
                            Icon(
                                if (ready.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(
                                    if (ready.isPinned) R.string.decoy_notes_unpin_note else R.string.decoy_notes_pin_note
                                )
                            )
                        }
                        if (!ready.isNew) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.decoy_note_editor_delete))
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (ready == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = ready.title,
                    onValueChange = { viewModel.save(it, ready.body, ready.isPinned) },
                    placeholder = { Text(stringResource(R.string.decoy_note_editor_title_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = ready.body,
                    onValueChange = { viewModel.save(ready.title, it, ready.isPinned) },
                    placeholder = { Text(stringResource(R.string.decoy_note_editor_body_hint)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.decoy_note_delete_title)) },
            text = { Text(stringResource(R.string.decoy_note_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                        onBack()
                    }
                ) { Text(stringResource(R.string.decoy_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.decoy_action_cancel))
                }
            }
        )
    }
}
