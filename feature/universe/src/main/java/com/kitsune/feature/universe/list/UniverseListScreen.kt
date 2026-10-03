package com.kitsune.feature.universe.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.common.generation.GenerationFailureCategory
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.failureCategory
import com.kitsune.core.designsystem.BugReportPrivacyDialog
import com.kitsune.core.designsystem.SwipeToDeleteRow
import com.kitsune.feature.universe.R

/** Content-only (no own Scaffold/TopAppBar/FAB) — embedded as the "Univers" tab of `HomeScreen`. */
@Composable
fun UniverseListScreen(
    onOpenUniverse: (universeId: String) -> Unit,
    onOpenDraftReview: (jobId: String) -> Unit,
    viewModel: UniverseListViewModel = hiltViewModel()
) {
    val universes by viewModel.universes.collectAsStateWithLifecycle()
    val generationJobs by viewModel.generationJobs.collectAsStateWithLifecycle()
    val bugReportSending by viewModel.bugReportSending.collectAsStateWithLifecycle()
    var detailJobId by remember { mutableStateOf<String?>(null) }
    var reportJobId by remember { mutableStateOf<String?>(null) }

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

    if (universes.isEmpty() && generationJobs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    stringResource(R.string.universe_list_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    stringResource(R.string.universe_list_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                GenerationJobsBanner(
                    jobs = generationJobs,
                    onOpenDraftReview = onOpenDraftReview,
                    onOpenFailedJob = { detailJobId = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(universes, key = { it.id }) { universe ->
                SwipeToDeleteRow(
                    onDelete = { viewModel.deleteUniverse(universe) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(universe.name) },
                        supportingContent = {
                            val subtitle = listOfNotNull(
                                universe.genre.takeIf { it.isNotBlank() },
                                universe.description.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier.clickable { onOpenUniverse(universe.id) }.fillMaxWidth()
                    )
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
                            stringResource(R.string.universe_list_jobs_active_format, activeJobs.size),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            stringResource(R.string.universe_list_jobs_ready_format, readyJobs.size),
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
                        stringResource(R.string.universe_list_jobs_failed_format, failedJobs.size),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
