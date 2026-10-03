package com.kitsune.feature.chat.novel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.feature.chat.R
import com.kitsune.feature.chat.formatRoleplayText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelModeScreen(
    onBack: () -> Unit,
    viewModel: NovelModeViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val personaName by viewModel.personaName.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.let { stream -> viewModel.exportPdf(stream) }
        }
    }

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is PdfExportState.Success -> {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.novel_export_success)) }
                viewModel.resetExportState()
            }
            is PdfExportState.Error -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                viewModel.resetExportState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(personaName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (exportState != PdfExportState.Exporting) {
                                exportLauncher.launch("${personaName.ifBlank { "roman" }}.pdf")
                            }
                        }
                    ) {
                        if (exportState == PdfExportState.Exporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = stringResource(R.string.novel_export_action)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(scrollState)
        ) {
            messages.forEachIndexed { index, message ->
                when (message.role) {
                    MessageRole.USER -> UserMessage(message)
                    MessageRole.ASSISTANT -> AssistantMessage(message)
                    MessageRole.SYSTEM -> SystemMessage(message)
                    // Model-facing style directive: never part of the novel's prose.
                    MessageRole.STYLE_DIRECTIVE -> Unit
                }
                if (index < messages.size - 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun UserMessage(message: MessageEntity) {
    val actionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val asideColor = MaterialTheme.colorScheme.tertiary

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Vous :",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatRoleplayText(message.content, actionColor, asideColor),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AssistantMessage(message: MessageEntity) {
    val actionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val asideColor = MaterialTheme.colorScheme.tertiary

    Text(
        text = formatRoleplayText(message.content, actionColor, asideColor),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SystemMessage(message: MessageEntity) {
    Text(
        text = message.content,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
