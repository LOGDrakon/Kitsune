package com.kitsune.feature.persona.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.common.style.PersonaTemplate
import com.kitsune.core.common.style.PersonaTemplates
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.designsystem.TagsEditor
import com.kitsune.feature.persona.R

@Composable
fun PersonaCreationScreen(
    onSaved: (personaId: String) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PersonaCreationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val initialDescription by viewModel.initialDescription.collectAsStateWithLifecycle()

    when (val current = state) {
        is PersonaCreationUiState.RequiresProfileSetup -> RequiresProfileSetupStep(
            onOpenSettings = onOpenSettings,
            onCancel = onCancel
        )
        is PersonaCreationUiState.DescribeInput -> DescribeStep(
            initialDescription = initialDescription,
            onCancel = onCancel,
            onGenerate = viewModel::generate
        )
        is PersonaCreationUiState.Generating -> GeneratingStep(current.count)
        is PersonaCreationUiState.Scheduled -> ScheduledStep(
            description = current.description,
            onBack = onCancel
        )
        is PersonaCreationUiState.GenerationError -> ErrorStep(current.message, onRetry = viewModel::retryFromDescription)
        is PersonaCreationUiState.Browsing -> BrowsingStep(
            state = current,
            onPrevious = viewModel::showPreviousProposal,
            onNext = viewModel::showNextProposal,
            onSelect = viewModel::selectCurrentProposal,
            onFinish = onCancel
        )
        is PersonaCreationUiState.Review -> ReviewStep(
            state = current,
            onAgeChange = viewModel::updateAge,
            onToggleTag = viewModel::toggleMaturityTag,
            onAddTag = viewModel::addTag,
            onRemoveTag = viewModel::removeTag,
            onPhysicalTraitsChange = viewModel::updatePhysicalTraits,
            onArtStyleChange = viewModel::updateArtStyle,
            onColorPaletteChange = viewModel::updateColorPalette,
            onDefaultOutfitChange = viewModel::updateDefaultOutfit,
            onSave = { viewModel.save(onSaved) }
        )
    }
}

/** Shown instead of the normal creation flow when this would be the user's very first persona and
 * their own profile (Réglages > Profil et langue) is still blank — see `PersonaCreationViewModel.init`. */
@Composable
private fun RequiresProfileSetupStep(
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.persona_create_profile_gate_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            stringResource(R.string.persona_create_profile_gate_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            Text(stringResource(R.string.persona_create_profile_gate_action))
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DescribeStep(
    initialDescription: String = "",
    onCancel: () -> Unit,
    onGenerate: (String, PersonaTemplate?, Int) -> Unit
) {
    var description by remember { mutableStateOf(initialDescription) }
    var selectedTemplate by remember { mutableStateOf<PersonaTemplate?>(null) }
    var proposalCount by remember { mutableStateOf(1) }

    // initialDescription starts blank and is populated asynchronously (only when this screen was
    // opened "from" an existing NPC — see PersonaCreationViewModel.fromNpcId) — seed the field once
    // it arrives, without ever overwriting text the user has since typed themselves.
    LaunchedEffect(initialDescription) {
        if (initialDescription.isNotBlank() && description.isBlank()) {
            description = initialDescription
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.persona_create_describe_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.persona_create_description_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )

        Text(
            stringResource(R.string.persona_create_visual_style_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PersonaTemplates.all.forEach { template ->
                FilterChip(
                    selected = selectedTemplate?.id == template.id,
                    onClick = {
                        selectedTemplate = if (selectedTemplate?.id == template.id) null else template
                    },
                    label = { Text(template.name) }
                )
            }
        }
        selectedTemplate?.let {
            Text(
                it.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Text(
            stringResource(R.string.persona_create_proposal_count_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PERSONA_PROPOSAL_COUNT_OPTIONS.forEach { count ->
                FilterChip(
                    selected = proposalCount == count,
                    onClick = { proposalCount = count },
                    label = { Text(count.toString()) }
                )
            }
        }
        if (proposalCount > 1) {
            Text(
                stringResource(R.string.persona_create_proposal_count_hint, proposalCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = { onGenerate(description, selectedTemplate, proposalCount) }, enabled = description.isNotBlank()) {
                Text(stringResource(R.string.action_generate))
            }
        }
    }
}

@Composable
private fun GeneratingStep(count: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            if (count <= 1) {
                stringResource(R.string.persona_create_generating_single)
            } else {
                stringResource(R.string.persona_create_generating_multiple, count)
            },
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            stringResource(R.string.persona_create_generating_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ScheduledStep(description: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.persona_create_scheduled_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            stringResource(R.string.persona_create_scheduled_message, description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.persona_create_back_to_list_button))
        }
    }
}

@Composable
private fun BrowsingStep(
    state: PersonaCreationUiState.Browsing,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: () -> Unit,
    onFinish: () -> Unit
) {
    val draft = state.proposals[state.currentIndex]
    val isSaved = state.currentIndex in state.savedIndices

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.persona_create_proposal_counter, state.currentIndex + 1, state.proposals.size),
                style = MaterialTheme.typography.labelLarge
            )
            if (state.savedIndices.isNotEmpty()) {
                Text(
                    stringResource(R.string.persona_create_saved_count_format, state.savedIndices.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
            Text(draft.name, style = MaterialTheme.typography.headlineSmall)
            Text(draft.description, modifier = Modifier.padding(top = 8.dp))
            Text(draft.personality, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            if (draft.scenario.isNotBlank()) {
                Text(
                    stringResource(R.string.persona_create_scenario_label, draft.scenario),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (draft.firstMessage.isNotBlank()) {
                Text(
                    stringResource(R.string.persona_create_first_message_label, draft.firstMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onPrevious,
                enabled = state.currentIndex > 0,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.persona_create_previous_button)) }
            TextButton(
                onClick = onNext,
                enabled = state.currentIndex < state.proposals.size - 1,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.persona_create_next_button)) }
        }
        Button(
            onClick = onSelect,
            enabled = !state.isPreparingSelection && !isSaved,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            if (state.isPreparingSelection) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(end = 8.dp))
            }
            Text(stringResource(if (isSaved) R.string.persona_create_saved_check else R.string.persona_create_choose_button))
        }
        OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.persona_create_finish_button))
        }
    }
}

@Composable
private fun ErrorStep(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.persona_create_error_title), style = MaterialTheme.typography.headlineSmall)
        Text(message, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun ReviewStep(
    state: PersonaCreationUiState.Review,
    onAgeChange: (String) -> Unit,
    onToggleTag: (MaturityTag) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onPhysicalTraitsChange: (String) -> Unit,
    onArtStyleChange: (String) -> Unit,
    onColorPaletteChange: (String) -> Unit,
    onDefaultOutfitChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(state.draft.name, style = MaterialTheme.typography.headlineSmall)
        Text(state.draft.description, modifier = Modifier.padding(top = 8.dp))
        Text(state.draft.personality, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

        OutlinedTextField(
            value = state.age,
            onValueChange = onAgeChange,
            label = { Text(stringResource(R.string.persona_review_age_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )

        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaturityTag.entries.forEach { tag ->
                FilterChip(
                    selected = tag in state.maturityTags,
                    onClick = { onToggleTag(tag) },
                    label = { Text(tag.name) }
                )
            }
        }

        TagsEditor(
            tags = state.tags,
            onAddTag = onAddTag,
            onRemoveTag = onRemoveTag,
            label = stringResource(R.string.persona_review_tags_label),
            placeholder = stringResource(R.string.tags_add_placeholder),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )

        Text(
            stringResource(R.string.persona_review_visual_sheet_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 24.dp)
        )
        OutlinedTextField(
            value = state.visualSheet.physicalTraits,
            onValueChange = onPhysicalTraitsChange,
            label = { Text(stringResource(R.string.persona_review_physical_traits_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = state.visualSheet.artStyle,
            onValueChange = onArtStyleChange,
            label = { Text(stringResource(R.string.persona_review_art_style_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = state.visualSheet.colorPalette,
            onValueChange = onColorPaletteChange,
            label = { Text(stringResource(R.string.persona_review_color_palette_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = state.visualSheet.defaultOutfit,
            onValueChange = onDefaultOutfitChange,
            label = { Text(stringResource(R.string.persona_review_default_outfit_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(end = 8.dp))
            }
            Text(stringResource(R.string.action_save))
        }
    }
}
