package com.kitsune.feature.persona.tonelibrary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.kitsune.core.data.local.entities.ToneCardEntity
import com.kitsune.feature.persona.R
import com.kitsune.feature.persona.detail.ToneCardDialog

/**
 * The user's own library of story tones (2026-08-24).
 *
 * Reached from Settings, and deliberately not from a persona: these describe how *this player* likes
 * stories told — "plot twists, new faces often, intrigue, drama" — so they apply to every new
 * conversation whoever the character is. The story card offers them alongside the tones written for
 * the specific persona and the built-in recipes.
 *
 * Mature registers are allowed here without a tag check, unlike on a persona sheet: there is no
 * character to check against, and the story card applies the same gate at the point of use — a
 * profile card asking for explicit prose simply will not be offered on a persona that forbids it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToneLibraryScreen(
    onBack: () -> Unit,
    viewModel: ToneLibraryViewModel = hiltViewModel()
) {
    val toneCards by viewModel.toneCards.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ToneLibraryEdit?>(null) }

    editing?.let { current ->
        val existing = (current as? ToneLibraryEdit.Existing)?.card
        ToneCardDialog(
            initial = existing,
            allowMatureModes = true,
            onDismiss = { editing = null },
            onSave = { draft ->
                viewModel.save(existing?.id, draft)
                editing = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tone_library_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ToneLibraryEdit.New }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tone_library_add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.tone_library_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (toneCards.isEmpty()) {
                Text(
                    stringResource(R.string.tone_library_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
                return@Column
            }

            toneCards.forEach { card ->
                ToneLibraryCard(
                    card = card,
                    onEdit = { editing = ToneLibraryEdit.Existing(card) },
                    onDelete = { viewModel.delete(card) }
                )
            }
        }
    }
}

@Composable
private fun ToneLibraryCard(card: ToneCardEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember(card.id) { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.tone_card_delete_confirm)) },
            text = { Text(card.name) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    confirmDelete = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable(onClick = onEdit)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleSmall)
                if (card.description.isNotBlank()) {
                    Text(
                        card.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (card.directive.isNotBlank()) {
                    Text(
                        card.directive,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private sealed interface ToneLibraryEdit {
    data object New : ToneLibraryEdit
    data class Existing(val card: ToneCardEntity) : ToneLibraryEdit
}
