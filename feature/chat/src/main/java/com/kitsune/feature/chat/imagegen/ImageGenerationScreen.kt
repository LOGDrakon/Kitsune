package com.kitsune.feature.chat.imagegen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kitsune.core.common.inspiration.InspirationDraftHolder
import com.kitsune.core.designsystem.DecryptedImage
import com.kitsune.core.network.repository.ASPECT_RATIO_OPTIONS
import com.kitsune.feature.chat.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageGenerationScreen(
    onBack: () -> Unit,
    onOpenInspirationWizard: () -> Unit,
    viewModel: ImageGenerationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val firstImageFree by viewModel.firstImageFree.collectAsStateWithLifecycle()
    val imageCostCredits by viewModel.imageCostCredits.collectAsStateWithLifecycle()
    val imageHdCostCredits by viewModel.imageHdCostCredits.collectAsStateWithLifecycle()
    var galleryConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ImageGenerationEvent.SentToChat -> onBack()
                ImageGenerationEvent.SavedToGallery -> galleryConfirmation = true
            }
        }
    }

    // Picked up on return from the "Manque d'inspiration ?" wizard (InspirationTarget.IMAGE) —
    // this LaunchedEffect re-fires because Navigation-Compose recomposes this screen fresh each
    // time it's navigated back to, unlike the ViewModel (same instance, its init{} won't rerun).
    LaunchedEffect(Unit) {
        InspirationDraftHolder.consume()?.let { viewModel.updateDescription(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.image_gen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val current = state) {
            is ImageGenerationUiState.Loading -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            is ImageGenerationUiState.Error -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is ImageGenerationUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        stringResource(R.string.image_gen_for_characters_label, current.characterNames),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = current.description,
                        onValueChange = viewModel::updateDescription,
                        label = { Text(stringResource(R.string.image_gen_description_label)) },
                        placeholder = { Text(stringResource(R.string.image_gen_description_placeholder)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    OutlinedButton(
                        onClick = viewModel::describeCurrentScene,
                        enabled = !current.isDescribingScene && !current.isGenerating,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        if (current.isDescribingScene) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        } else {
                            Text(stringResource(R.string.image_gen_from_current_scene_button))
                        }
                    }
                    Text(
                        stringResource(R.string.image_gen_auto_fill_description_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedButton(
                        onClick = onOpenInspirationWizard,
                        enabled = !current.isDescribingScene && !current.isGenerating,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.image_gen_inspiration_button))
                    }

                    Text(
                        stringResource(R.string.image_gen_visual_style_label),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        viewModel.styleOptions.forEach { style ->
                            FilterChip(
                                selected = current.selectedStyle?.id == style.id,
                                onClick = {
                                    viewModel.selectStyle(if (current.selectedStyle?.id == style.id) null else style)
                                },
                                label = { Text(style.name) }
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.image_gen_aspect_ratio_label),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ASPECT_RATIO_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = current.selectedAspectRatio == option.value,
                                onClick = { viewModel.selectAspectRatio(option.value) },
                                label = { Text(option.label) }
                            )
                        }
                    }

                    if (current.showAvatarReferenceOption) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        ) {
                            Text(stringResource(R.string.image_gen_use_avatar_reference_label), modifier = Modifier.weight(1f))
                            Switch(checked = current.useAvatarReference, onCheckedChange = viewModel::setUseAvatarReference)
                        }
                        Text(
                            stringResource(R.string.image_gen_avatar_reference_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    current.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    if (current.wasRefused) {
                        OutlinedButton(
                            onClick = viewModel::generateSfwFallback,
                            enabled = !current.isGenerating,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(stringResource(R.string.image_gen_generate_anyway_button))
                        }
                        Text(
                            stringResource(R.string.image_gen_soften_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Text(
                            stringResource(R.string.image_gen_hd_quality_label, imageHdCostCredits),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = current.useHdQuality, onCheckedChange = viewModel::setUseHdQuality)
                    }
                    Text(
                        stringResource(R.string.image_gen_hd_quality_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Text(
                        if (firstImageFree) stringResource(R.string.image_credit_cost_first_free)
                        else stringResource(
                            R.string.image_credit_cost,
                            if (current.useHdQuality) imageHdCostCredits else imageCostCredits
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Button(
                        onClick = viewModel::generate,
                        enabled = !current.isGenerating && current.description.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        if (current.isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(if (current.generatedImage == null) stringResource(R.string.image_gen_generate_button) else stringResource(R.string.image_gen_generate_new_button))
                        }
                    }

                    current.generatedImage?.let { bytes ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        ) {
                            DecryptedImage(
                                bytes = bytes,
                                contentDescription = current.description,
                                modifier = Modifier.fillMaxWidth().height(320.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                        if (current.wasSfwFallback) {
                            Text(
                                stringResource(R.string.image_gen_sfw_fallback_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = viewModel::regenerate,
                            enabled = !current.isGenerating,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.image_gen_regenerate_button))
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            if (current.galleryTargets.isNotEmpty()) {
                                var showTargetPicker by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = {
                                            galleryConfirmation = false
                                            if (current.galleryTargets.size == 1) {
                                                viewModel.saveToGallery(current.galleryTargets.first().personaId)
                                            } else {
                                                showTargetPicker = true
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (galleryConfirmation) stringResource(R.string.image_gen_saved_confirmation) else stringResource(R.string.image_gen_persona_gallery_button))
                                    }
                                    DropdownMenu(expanded = showTargetPicker, onDismissRequest = { showTargetPicker = false }) {
                                        current.galleryTargets.forEach { target ->
                                            DropdownMenuItem(
                                                text = { Text(target.personaName) },
                                                onClick = {
                                                    viewModel.saveToGallery(target.personaId)
                                                    showTargetPicker = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = viewModel::sendToChat,
                                modifier = Modifier.weight(1f).padding(start = if (current.galleryTargets.isNotEmpty()) 8.dp else 0.dp)
                            ) {
                                Text(stringResource(R.string.image_gen_send_to_chat_button))
                            }
                        }
                    }
                }
            }
        }
    }
}
