package com.kitsune.feature.universe.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.designsystem.AiQuickGenerateSection
import com.kitsune.core.common.style.MAX_NAME_LENGTH
import com.kitsune.core.designsystem.OfudaAmount
import com.kitsune.core.designsystem.TagsEditor
import com.kitsune.core.network.repository.UniverseBundleDraft
import com.kitsune.feature.universe.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniverseCreationScreen(
    onSaved: (universeId: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: UniverseCreationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val limitReached by viewModel.limitReached.collectAsStateWithLifecycle()
    val firstCreationFree by viewModel.firstCreationFree.collectAsStateWithLifecycle()
    val universeGenCost by viewModel.universeGenCostCredits.collectAsStateWithLifecycle()

    if (limitReached) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLimitReached,
            title = { Text(stringResource(R.string.universe_limit_reached_title)) },
            text = { Text(stringResource(R.string.universe_limit_reached_text, MAX_FREE_UNIVERSES)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissLimitReached) { Text(stringResource(R.string.universe_detail_error_ok)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.universe_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    ) { padding ->
        when (val current = state) {
            is UniverseCreationUiState.Form -> FormStep(
                state = current,
                firstCreationFree = firstCreationFree,
                unitCostCredits = universeGenCost,
                onUpdateName = viewModel::updateName,
                onUpdateDescription = viewModel::updateDescription,
                onUpdateGenre = viewModel::updateGenre,
                onUpdateVisualStyle = viewModel::updateVisualStyle,
                onUpdateAiDescription = viewModel::updateAiDescription,
                onUpdateProposalCount = viewModel::updateProposalCount,
                onAddTag = viewModel::addTag,
                onRemoveTag = viewModel::removeTag,
                onGenerateProposals = viewModel::generateProposals,
                onSave = { viewModel.save(onSaved) },
                modifier = Modifier.padding(padding)
            )
            is UniverseCreationUiState.Generating -> GeneratingStep(current.count, Modifier.padding(padding))
            is UniverseCreationUiState.Scheduled -> ScheduledStep(
                description = current.description,
                onBack = onCancel,
                modifier = Modifier.padding(padding)
            )
            is UniverseCreationUiState.GenerationError -> ErrorStep(
                message = current.message,
                onRetry = viewModel::retryFromForm,
                modifier = Modifier.padding(padding)
            )
            is UniverseCreationUiState.Browsing -> BrowsingStep(
                state = current,
                onPrevious = viewModel::showPreviousProposal,
                onNext = viewModel::showNextProposal,
                onSelect = viewModel::selectCurrentProposal,
                onFinish = onCancel,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun FormStep(
    state: UniverseCreationUiState.Form,
    firstCreationFree: Boolean,
    /** Ofudas facturés par proposition (poussé par le serveur). */
    unitCostCredits: Int = 4,
    onUpdateName: (String) -> Unit,
    onUpdateDescription: (String) -> Unit,
    onUpdateGenre: (String) -> Unit,
    onUpdateVisualStyle: (String) -> Unit,
    onUpdateAiDescription: (String) -> Unit,
    onUpdateProposalCount: (Int) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onGenerateProposals: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCostConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        AiQuickGenerateSection(
            description = state.aiDescription,
            onDescriptionChange = onUpdateAiDescription,
            isGenerating = false,
            onGenerate = { showCostConfirm = true }
        )

        Text(
            stringResource(R.string.universe_create_proposal_count_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UNIVERSE_PROPOSAL_COUNT_OPTIONS.forEach { count ->
                FilterChip(
                    selected = state.proposalCount == count,
                    onClick = { onUpdateProposalCount(count) },
                    label = { Text(count.toString()) }
                )
            }
        }
        Text(
            stringResource(R.string.universe_create_proposal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
        Text(
            stringResource(R.string.universe_create_manual_section_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = onUpdateName,
            label = { Text(stringResource(R.string.field_name)) },
            supportingText = { Text("${state.name.length}/$MAX_NAME_LENGTH") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.description,
            onValueChange = onUpdateDescription,
            label = { Text(stringResource(R.string.field_description)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        OutlinedTextField(
            value = state.genre,
            onValueChange = onUpdateGenre,
            label = { Text(stringResource(R.string.universe_create_genre_label)) },
            placeholder = { Text(stringResource(R.string.universe_create_genre_placeholder)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        OutlinedTextField(
            value = state.visualStyle,
            onValueChange = onUpdateVisualStyle,
            label = { Text(stringResource(R.string.universe_create_visual_style_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        TagsEditor(
            tags = state.tags,
            onAddTag = onAddTag,
            onRemoveTag = onRemoveTag,
            label = stringResource(R.string.universe_create_tags_label),
            placeholder = stringResource(R.string.universe_tags_add_placeholder),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.universe_create_submit))
        }
    }

    if (showCostConfirm) {
        AlertDialog(
            onDismissRequest = { showCostConfirm = false },
            title = {
                Text(
                    stringResource(
                        if (firstCreationFree) R.string.universe_create_first_free_title
                        else R.string.universe_create_cost_confirm_title
                    )
                )
            },
            text = {
                if (firstCreationFree) {
                    Text(stringResource(R.string.universe_create_first_free_text))
                } else {
                    Column {
                        Text(stringResource(R.string.universe_create_cost_confirm_text, state.proposalCount))
                        // Une proposition = un appel facturé : le coût total est le produit, et non
                        // le simple nombre de propositions comme affiché auparavant.
                        OfudaAmount(amount = state.proposalCount * unitCostCredits, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCostConfirm = false
                    onGenerateProposals()
                }) { Text(stringResource(R.string.universe_detail_action_generate)) }
            },
            dismissButton = {
                TextButton(onClick = { showCostConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun GeneratingStep(count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            if (count == 1) stringResource(R.string.universe_create_generating_single) else stringResource(R.string.universe_create_generating_multiple_format, count),
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            stringResource(R.string.universe_create_generating_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ScheduledStep(description: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.universe_create_scheduled_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            stringResource(R.string.universe_create_scheduled_message_format, description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.universe_create_back_to_list))
        }
    }
}

@Composable
private fun ErrorStep(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.universe_create_error_title), style = MaterialTheme.typography.headlineSmall)
        Text(message, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.universe_create_retry)) }
    }
}

@Composable
private fun BrowsingStep(
    state: UniverseCreationUiState.Browsing,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val proposal = state.proposals[state.currentIndex]
    val isSaved = state.currentIndex in state.savedIndices

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.universe_create_proposal_index_format, state.currentIndex + 1, state.proposals.size),
                style = MaterialTheme.typography.labelLarge
            )
            if (state.savedIndices.isNotEmpty()) {
                Text(
                    stringResource(R.string.universe_create_saved_count_format, state.savedIndices.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
            UniverseProposalCard(proposal)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = state.currentIndex > 0,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.universe_create_previous)) }
            OutlinedButton(
                onClick = onNext,
                enabled = state.currentIndex < state.proposals.size - 1,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.universe_create_next)) }
        }
        Button(
            onClick = onSelect,
            enabled = !isSaved,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(if (isSaved) stringResource(R.string.universe_create_saved_check) else stringResource(R.string.universe_create_select_this))
        }
        OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.universe_create_finish))
        }
    }
}

@Composable
private fun UniverseProposalCard(proposal: UniverseBundleDraft) {
    Column {
        Text(proposal.universe.name, style = MaterialTheme.typography.headlineSmall)
        if (proposal.universe.genre.isNotBlank()) {
            Text(
                proposal.universe.genre,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(proposal.universe.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        if (proposal.universe.visualStyle.isNotBlank()) {
            Text(
                stringResource(R.string.universe_create_visual_style_format, proposal.universe.visualStyle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (proposal.factions.isNotEmpty()) {
            SectionLabel(stringResource(R.string.universe_create_section_factions))
            proposal.factions.forEach { faction ->
                EntryCard(
                    title = faction.name,
                    subtitle = faction.type + (faction.alignment.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    description = faction.description
                )
            }
        }
        if (proposal.locations.isNotEmpty()) {
            SectionLabel(stringResource(R.string.universe_create_section_locations))
            proposal.locations.forEach { location ->
                EntryCard(title = location.name, subtitle = location.type, description = location.description)
            }
        }
        if (proposal.npcs.isNotEmpty()) {
            SectionLabel(stringResource(R.string.universe_create_section_npcs))
            proposal.npcs.forEach { npc ->
                EntryCard(title = npc.name, subtitle = npc.role, description = npc.description)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun EntryCard(title: String, subtitle: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
