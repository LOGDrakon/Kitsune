package com.kitsune.app.inspiration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kitsune.core.network.inspiration.PersonaSketch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.app.R
import com.kitsune.core.network.inspiration.InspirationTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationWizardScreen(
    onBack: () -> Unit,
    onDescriptionReady: (target: InspirationTarget, description: String) -> Unit,
    viewModel: InspirationWizardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val readyEvent by viewModel.readyEvent.collectAsStateWithLifecycle()

    LaunchedEffect(readyEvent) {
        readyEvent?.let { description -> onDescriptionReady(viewModel.target, description) }
    }

    val titleRes = when (viewModel.target) {
        InspirationTarget.UNIVERSE -> R.string.inspiration_wizard_title_universe
        InspirationTarget.IMAGE -> R.string.inspiration_wizard_title_image
        InspirationTarget.PERSONA -> R.string.inspiration_wizard_title_persona
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val current = state) {
                is InspirationWizardUiState.LoadingQuestion -> LoadingStep(stringResource(R.string.inspiration_wizard_loading_question))
                is InspirationWizardUiState.Synthesizing -> LoadingStep(stringResource(R.string.inspiration_wizard_synthesizing))
                is InspirationWizardUiState.Error -> ErrorStep(current.message, onRetry = viewModel::retry, onBack = onBack)
                is InspirationWizardUiState.Asking -> AskingStep(
                    state = current,
                    onAnswerChange = viewModel::updateAnswer,
                    onSkip = { viewModel.submitAnswer(skip = true) },
                    onNext = { viewModel.submitAnswer(skip = false) }
                )
                is InspirationWizardUiState.ChoosingMode -> ChoosingModeStep(
                    onInterview = viewModel::chooseInterviewMode,
                    onExplore = viewModel::chooseExplorationMode
                )
                is InspirationWizardUiState.Briefing -> BriefingStep(
                    state = current,
                    onBriefChange = viewModel::updateBrief,
                    onSubmit = viewModel::submitBrief
                )
                is InspirationWizardUiState.LoadingSketches ->
                    LoadingStep(stringResource(R.string.inspiration_wizard_loading_sketches))
                is InspirationWizardUiState.Sketching -> SketchingStep(
                    state = current,
                    onSelect = viewModel::selectSketch,
                    onNoteChange = viewModel::updateSketchNote,
                    onRefine = viewModel::refineFromSelection,
                    onBuild = viewModel::buildFromSelection
                )
            }
        }
    }
}

/**
 * The fork shown first (2026-08-24).
 *
 * Being stuck comes in two shapes, and the wizard only ever served one of them: the interview asks
 * four open questions, which is exactly the wrong instrument for someone whose honest answer to all
 * four is "I don't know". Exploration inverts it — the AI proposes, the user reacts, and reacting is
 * far easier than inventing.
 */
@Composable
private fun ChoosingModeStep(onInterview: () -> Unit, onExplore: () -> Unit) {
    Text(
        stringResource(R.string.inspiration_wizard_mode_title),
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(16.dp))
    ModeCard(
        title = stringResource(R.string.inspiration_wizard_mode_interview_title),
        body = stringResource(R.string.inspiration_wizard_mode_interview_body),
        onClick = onInterview
    )
    Spacer(modifier = Modifier.height(8.dp))
    ModeCard(
        title = stringResource(R.string.inspiration_wizard_mode_explore_title),
        body = stringResource(R.string.inspiration_wizard_mode_explore_body),
        onClick = onExplore
    )
}

@Composable
private fun ModeCard(title: String, body: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun BriefingStep(
    state: InspirationWizardUiState.Briefing,
    onBriefChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Text(stringResource(R.string.inspiration_wizard_brief_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.inspiration_wizard_brief_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = state.brief,
        onValueChange = onBriefChange,
        placeholder = { Text(stringResource(R.string.inspiration_wizard_brief_placeholder)) },
        modifier = Modifier.fillMaxWidth().height(120.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onSubmit,
        enabled = state.brief.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.inspiration_wizard_brief_submit)) }
}

/**
 * One round of the exploration loop.
 *
 * The note field sits under the selection rather than beside each card on purpose: it applies to the
 * chosen one, and it is what turns "none of these, but the second is closest" into the instruction
 * the next round is built on.
 */
@Composable
private fun SketchingStep(
    state: InspirationWizardUiState.Sketching,
    onSelect: (PersonaSketch) -> Unit,
    onNoteChange: (String) -> Unit,
    onRefine: () -> Unit,
    onBuild: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.inspiration_wizard_sketch_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.inspiration_wizard_sketch_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        state.sketches.forEach { sketch ->
            val isSelected = sketch == state.selected
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { onSelect(sketch) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (sketch.name.isNotBlank()) {
                        Text(sketch.name, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        sketch.concept,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (sketch.hook.isNotBlank()) {
                        Text(
                            sketch.hook,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        if (state.selected != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.inspiration_wizard_sketch_note_label)) },
                placeholder = { Text(stringResource(R.string.inspiration_wizard_sketch_note_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canRefine) {
                    OutlinedButton(onClick = onRefine, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.inspiration_wizard_sketch_refine))
                    }
                }
                Button(onClick = onBuild, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.inspiration_wizard_sketch_build))
                }
            }
            if (!state.canRefine) {
                Text(
                    stringResource(R.string.inspiration_wizard_sketch_last_round),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingStep(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorStep(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun AskingStep(
    state: InspirationWizardUiState.Asking,
    onAnswerChange: (String) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    val isLastQuestion = state.questionIndex == state.totalQuestions - 1

    LinearProgressIndicator(
        progress = { (state.questionIndex + 1f) / state.totalQuestions },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        stringResource(R.string.inspiration_wizard_progress_format, state.questionIndex + 1, state.totalQuestions),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(state.question, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = state.answer,
        onValueChange = onAnswerChange,
        label = { Text(stringResource(R.string.inspiration_wizard_answer_label)) },
        placeholder = { Text(stringResource(R.string.inspiration_wizard_answer_placeholder)) },
        modifier = Modifier.fillMaxWidth().height(120.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.inspiration_wizard_skip_button))
        }
        Button(onClick = onNext, enabled = state.answer.isNotBlank(), modifier = Modifier.weight(1f)) {
            Text(stringResource(if (isLastQuestion) R.string.inspiration_wizard_finish_button else R.string.inspiration_wizard_next_button))
        }
    }
}
