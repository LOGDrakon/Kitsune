package com.kitsune.feature.persona.imagegen

import com.kitsune.core.designsystem.component.KitsunePage
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.common.inspiration.InspirationDraftHolder
import com.kitsune.core.designsystem.DecryptedImage
import com.kitsune.feature.persona.R

/** Persona-detail counterpart of `feature:chat`'s `ImageGenerationScreen` (demande explicite :
 * "réutiliser en partie la page de génération d'image in chat... rajouter les styles visuels et la
 * taille des images. Pas besoin du bouton générer depuis la scène actuelle puisqu'on est pas dans
 * une scène"). Same visual-style/aspect-ratio controls, minus the current-scene button and the
 * chat-sending/multi-target gallery picker — there is no chat here and only one possible gallery. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonaImageGenerationScreen(
    onBack: () -> Unit,
    onOpenInspirationWizard: () -> Unit,
    viewModel: PersonaImageGenerationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect { onBack() }
    }

    // Picked up on return from the "Manque d'inspiration ?" wizard (InspirationTarget.IMAGE) —
    // see ImageGenerationScreen's identical LaunchedEffect for why this must live in the
    // Composable rather than the ViewModel's init{}.
    LaunchedEffect(Unit) {
        InspirationDraftHolder.consume()?.let { viewModel.updateDescription(it) }
    }

    KitsunePage(
        title = stringResource(R.string.persona_image_gen_title),
        condensedTitle = true,
        onBack = onBack
    ) { padding ->
        when (val current = state) {
            is PersonaImageGenerationUiState.Loading -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            is PersonaImageGenerationUiState.Error -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is PersonaImageGenerationUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        stringResource(R.string.persona_image_gen_for_persona_label, current.personaName),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = current.description,
                        onValueChange = viewModel::updateDescription,
                        label = { Text(stringResource(R.string.persona_image_gen_description_label)) },
                        placeholder = { Text(stringResource(R.string.persona_image_gen_description_placeholder)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    OutlinedButton(
                        onClick = onOpenInspirationWizard,
                        enabled = !current.isGenerating,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.persona_image_gen_inspiration_button))
                    }

                    Text(
                        stringResource(R.string.persona_image_gen_visual_style_label),
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
                        stringResource(R.string.persona_image_gen_aspect_ratio_label),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        viewModel.aspectRatioOptions.forEach { option ->
                            FilterChip(
                                selected = current.selectedAspectRatio == option.value,
                                onClick = { viewModel.selectAspectRatio(option.value) },
                                label = { Text(option.label) }
                            )
                        }
                    }

                    if (current.hasAvatar) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        ) {
                            Text(stringResource(R.string.persona_image_gen_use_avatar_reference_label), modifier = Modifier.weight(1f))
                            Switch(checked = current.useAvatarReference, onCheckedChange = viewModel::setUseAvatarReference)
                        }
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
                            Text(stringResource(R.string.persona_image_gen_generate_anyway_button))
                        }
                    }

                    Button(
                        onClick = viewModel::generate,
                        enabled = !current.isGenerating && current.description.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        if (current.isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(if (current.generatedImage == null) stringResource(R.string.action_generate) else stringResource(R.string.persona_image_gen_generate_new_button))
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
                                contentScale = ContentScale.Fit
                            )
                        }
                        if (current.wasSfwFallback) {
                            Text(
                                stringResource(R.string.persona_image_gen_sfw_fallback_notice),
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
                            Text(stringResource(R.string.persona_image_gen_regenerate_button))
                        }

                        Button(
                            onClick = viewModel::saveToGallery,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.persona_image_gen_save_button))
                        }
                    }
                }
            }
        }
    }
}
