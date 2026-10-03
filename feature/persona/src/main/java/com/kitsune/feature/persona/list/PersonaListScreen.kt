package com.kitsune.feature.persona.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.common.generation.GenerationFailureCategory
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.failureCategory
import com.kitsune.core.designsystem.BugReportPrivacyDialog
import com.kitsune.core.designsystem.DecryptedImage
import com.kitsune.feature.persona.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonaListScreen(
    onOpenPersona: (personaId: String) -> Unit,
    onOpenDraftReview: (jobId: String) -> Unit,
    viewModel: PersonaListViewModel = hiltViewModel()
) {
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val generationJobs by viewModel.generationJobs.collectAsStateWithLifecycle()
    val avatarBytesById by viewModel.avatarBytesById.collectAsStateWithLifecycle()
    val bugReportSending by viewModel.bugReportSending.collectAsStateWithLifecycle()
    var selectedTags by remember { mutableStateOf(setOf<MaturityTag>()) }
    var detailJobId by remember { mutableStateOf<String?>(null) }
    var reportJobId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(personas) { viewModel.ensureAvatarsLoaded(personas) }

    val filteredPersonas = if (selectedTags.isEmpty()) {
        personas
    } else {
        personas.filter { persona -> persona.maturityTags.any { it in selectedTags } }
    }

    if (bugReportSending) {
        BugReportPrivacyDialog()
    }

    val detailJob = generationJobs.find { it.id == detailJobId }
    if (detailJob != null) {
        FailedGenerationDetailDialog(
            job = detailJob,
            onDismiss = { detailJobId = null },
            onRetry = {
                viewModel.retryFailedJob(detailJob)
                detailJobId = null
            },
            onReport = {
                detailJobId = null
                reportJobId = detailJob.id
            },
            onDelete = {
                viewModel.dismissFailedJob(detailJob)
                detailJobId = null
            }
        )
    }

    val reportJob = reportJobId
    if (reportJob != null) {
        GenerationBugReportDialog(
            onDismiss = { reportJobId = null },
            onSubmit = { subject, description ->
                viewModel.reportFailedJob(reportJob, subject, description) {}
                reportJobId = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GenerationJobsBanner(
            jobs = generationJobs,
            onOpenDraftReview = onOpenDraftReview,
            onOpenFailedJob = { detailJobId = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (personas.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaturityTag.entries.forEach { tag ->
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = {
                            selectedTags = if (tag in selectedTags) {
                                selectedTags - tag
                            } else {
                                selectedTags + tag
                            }
                        },
                        label = { Text(tag.name) }
                    )
                }
            }
        }

        if (personas.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        stringResource(R.string.persona_list_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        stringResource(R.string.persona_list_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else if (filteredPersonas.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.persona_list_empty_filtered_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredPersonas, key = PersonaEntity::id) { persona ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPersona(persona.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PersonaAvatar(
                            bytes = persona.avatarImageId?.let { avatarBytesById[it] },
                            name = persona.name
                        )
                        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(
                                text = persona.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (persona.maturityTags.isNotEmpty()) {
                                Text(
                                    text = persona.maturityTags.joinToString(" · ") { it.name },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationJobsBanner(
    jobs: List<GenerationJobEntity>,
    onOpenDraftReview: (jobId: String) -> Unit,
    onOpenFailedJob: (jobId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeJobs = jobs.filter { it.state == GenerationJobState.PENDING || it.state == GenerationJobState.RUNNING }
    val readyJobs = jobs.filter { it.state == GenerationJobState.SUCCEEDED }
    val failedJobs = jobs.filter { it.state == GenerationJobState.FAILED }

    if (activeJobs.isEmpty() && readyJobs.isEmpty() && failedJobs.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Active/ready share one row (priority: active while running, then ready) — unchanged from
        // before. Failed jobs get their OWN row below, always visible regardless of the other two,
        // so a failure is never hidden behind an active/ready job (previously it was — clicking it
        // did nothing since only readyJobs was clickable).
        if (activeJobs.isNotEmpty() || readyJobs.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = readyJobs.isNotEmpty()) {
                    readyJobs.firstOrNull()?.let { onOpenDraftReview(it.id) }
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (readyJobs.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeJobs.isNotEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (activeJobs.size > 1) stringResource(R.string.persona_list_active_generations_plural, activeJobs.size) else stringResource(R.string.persona_list_active_generation_single, activeJobs.size),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            if (readyJobs.size > 1) stringResource(R.string.persona_list_ready_plural, readyJobs.size) else stringResource(R.string.persona_list_ready_single, readyJobs.size),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (failedJobs.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    failedJobs.firstOrNull()?.let { onOpenFailedJob(it.id) }
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (failedJobs.size > 1) stringResource(R.string.persona_list_failed_plural, failedJobs.size) else stringResource(R.string.persona_list_failed_single, failedJobs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun categoryLabelRes(category: GenerationFailureCategory?): Int = when (category) {
    GenerationFailureCategory.CONTENT_POLICY -> R.string.generation_failure_category_content_policy
    GenerationFailureCategory.INSUFFICIENT_CREDITS -> R.string.generation_failure_category_insufficient_credits
    GenerationFailureCategory.PARSING_FAILED -> R.string.generation_failure_category_parsing_failed
    GenerationFailureCategory.TECHNICAL -> R.string.generation_failure_category_technical
    null -> R.string.generation_failure_category_unknown
}

/** Shown when tapping the failed-generation banner — what went wrong, whether a credit was
 * consumed for the attempt, and the raw technical error, plus retry/report/dismiss actions. */
@Composable
private fun FailedGenerationDetailDialog(
    job: GenerationJobEntity,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit
) {
    val category = job.failureCategory()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generation_failure_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(job.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(categoryLabelRes(category)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        when (category?.creditConsumed) {
                            true -> R.string.generation_failure_credit_consumed
                            false -> R.string.generation_failure_credit_not_consumed
                            null -> R.string.generation_failure_credit_unknown
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                job.errorMessage?.let { raw ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        raw,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    TextButton(onClick = onReport) { Text(stringResource(R.string.generation_failure_report_button)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.generation_failure_delete_button)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.generation_failure_retry_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun GenerationBugReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (subject: String, description: String) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generation_bug_report_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text(stringResource(R.string.generation_bug_report_subject_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.generation_bug_report_description_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(subject, description) },
                enabled = subject.isNotBlank() && description.isNotBlank()
            ) { Text(stringResource(R.string.generation_bug_report_send_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun PersonaAvatar(bytes: ByteArray?, name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (bytes != null) {
            DecryptedImage(
                bytes = bytes,
                contentDescription = stringResource(R.string.persona_list_avatar_content_description, name),
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = stringResource(R.string.persona_list_avatar_content_description, name))
                }
            }
        }
    }
}
