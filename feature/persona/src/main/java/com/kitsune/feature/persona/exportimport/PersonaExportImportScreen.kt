package com.kitsune.feature.persona.exportimport

import com.kitsune.core.designsystem.component.KitsunePage
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.feature.persona.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaExportImportScreen(
    personaId: String,
    personaName: String,
    onBack: () -> Unit,
    viewModel: PersonaExportImportViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showImportDialog by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportToFile(context, it, personaId)
        }
    }

    // Character cards (2026-08-25) — the interchange format Chub, SillyTavern, Agnai and RisuAI all
    // read. Separate launchers from the Kitsune-format ones above because the MIME type differs and
    // because the two formats carry different things: the Kitsune file keeps the gallery and the
    // encrypted images, the card keeps portability.
    val cardExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? -> uri?.let { viewModel.exportCardToFile(context, it, personaId) } }

    val cardImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.importCardFromFile(context, it) } }

    KitsunePage(
        title = stringResource(R.string.persona_export_import_title),
        condensedTitle = true,
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.persona_export_section_title, personaName),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.persona_export_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { exportLauncher.launch("persona_${personaName.replace(" ", "_")}.json") },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is PersonaExportImportUiState.Exporting
            ) {
                if (uiState is PersonaExportImportUiState.Exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_export))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                stringResource(R.string.persona_import_section_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.persona_import_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { showImportDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is PersonaExportImportUiState.Importing
            ) {
                if (uiState is PersonaExportImportUiState.Importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_import))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                stringResource(R.string.card_section_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.card_section_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { cardImportLauncher.launch("image/png") },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is PersonaExportImportUiState.Importing
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.card_import_action))
            }
            OutlinedButton(
                onClick = { cardExportLauncher.launch("${personaName.replace(" ", "_")}.png") },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is PersonaExportImportUiState.Exporting
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.card_export_action))
            }

            when (val state = uiState) {
                is PersonaExportImportUiState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            state.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                is PersonaExportImportUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            state.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> {}
            }
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text(stringResource(R.string.persona_import_section_title)) },
                text = {
                    OutlinedTextField(
                        value = importJson,
                        onValueChange = { importJson = it },
                        label = { Text(stringResource(R.string.persona_import_json_content_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 10
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.importFromJson(importJson)
                            showImportDialog = false
                            importJson = ""
                        },
                        enabled = importJson.isNotBlank()
                    ) {
Text(stringResource(R.string.action_import))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}
