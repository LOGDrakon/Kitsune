package com.kitsune.feature.settings.models

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.KitsuneActionSheet
import com.kitsune.core.designsystem.component.KitsuneEmptyState
import com.kitsune.core.designsystem.component.KitsuneNotice
import com.kitsune.core.designsystem.component.KitsunePage
import com.kitsune.core.designsystem.component.KitsuneRow
import com.kitsune.core.designsystem.component.PageTitle
import com.kitsune.core.designsystem.component.SectionHeader
import com.kitsune.core.designsystem.component.SheetAction
import com.kitsune.core.models.ModelCapabilityFilter
import com.kitsune.core.models.ModelPickerDialog
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.feature.settings.R
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz

private data class SlotSpec(val key: String, @StringRes val title: Int, @StringRes val hint: Int, val capability: ModelCapabilityFilter)

private val GROUPS: List<Pair<Int, List<SlotSpec>>> = listOf(
    R.string.models_group_chat to listOf(
        SlotSpec(LlmOperation.CHAT.name, R.string.models_op_chat, R.string.models_op_chat_hint, ModelCapabilityFilter.CHAT),
        SlotSpec(LlmOperation.NEXT_REPLY_SUGGESTIONS.name, R.string.models_op_suggestions, R.string.models_op_suggestions_hint, ModelCapabilityFilter.CHAT)
    ),
    R.string.models_group_memory to listOf(
        SlotSpec(LlmOperation.SUMMARY.name, R.string.models_op_summary, R.string.models_op_summary_hint, ModelCapabilityFilter.CHAT),
        SlotSpec(LlmOperation.LORE.name, R.string.models_op_lore, R.string.models_op_lore_hint, ModelCapabilityFilter.CHAT),
        SlotSpec(LlmOperation.EMBEDDING.name, R.string.models_op_embedding, R.string.models_op_embedding_hint, ModelCapabilityFilter.EMBEDDING)
    ),
    R.string.models_group_creation to listOf(
        SlotSpec(LlmOperation.QUICK_GENERATION.name, R.string.models_op_generation, R.string.models_op_generation_hint, ModelCapabilityFilter.CHAT),
        SlotSpec(LlmOperation.INSPIRATION.name, R.string.models_op_inspiration, R.string.models_op_inspiration_hint, ModelCapabilityFilter.CHAT),
        SlotSpec(LlmOperation.TRANSLATION.name, R.string.models_op_translation, R.string.models_op_translation_hint, ModelCapabilityFilter.CHAT)
    ),
    R.string.models_group_images to listOf(
        SlotSpec(LlmOperation.IMAGE_GENERATION.name, R.string.models_op_image, R.string.models_op_image_hint, ModelCapabilityFilter.IMAGE),
        SlotSpec(IMAGE_FALLBACK_SLOT, R.string.models_op_image_fallback, R.string.models_op_image_fallback_hint, ModelCapabilityFilter.IMAGE),
        SlotSpec(LlmOperation.VISUAL_SHEET.name, R.string.models_op_visual_sheet, R.string.models_op_visual_sheet_hint, ModelCapabilityFilter.CHAT),
        SlotSpec(LlmOperation.IMAGE_DESCRIPTION.name, R.string.models_op_image_description, R.string.models_op_image_description_hint, ModelCapabilityFilter.CHAT)
    )
)

/**
 * One model per kind of AI call. Every slot but chat, embeddings and images inherits the chat model
 * until it is set, so a user who only ever picks a chat model gets a working app — and one who wants
 * a cheap model for summaries and a strong one for the story can have exactly that.
 */
@Composable
fun ModelsScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val automatic by viewModel.automatic.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<SlotSpec?>(null) }
    var actionsFor by remember { mutableStateOf<SlotSpec?>(null) }

    KitsunePage(title = stringResource(R.string.models_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KitsuneTheme.spacing.gutter)
                .padding(bottom = KitsuneTheme.spacing.scrollBottom)
        ) {
            PageTitle(text = stringResource(R.string.models_title))
            if (!viewModel.hasProvider) {
                KitsuneEmptyState(
                    title = stringResource(R.string.models_no_provider_title),
                    body = stringResource(R.string.models_no_provider_body),
                    icon = Icons.Filled.Key,
                    actionLabel = stringResource(R.string.models_open_providers),
                    onAction = onOpenProviders
                )
                return@Column
            }
            Text(
                stringResource(R.string.models_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = KitsuneTheme.colors.textSecondary
            )
            KitsuneNotice(
                text = stringResource(R.string.models_embedding_notice),
                icon = Icons.Filled.Info,
                modifier = Modifier.padding(top = KitsuneTheme.spacing.md)
            )
            GROUPS.forEach { (groupTitle, specs) ->
                Spacer(Modifier.height(KitsuneTheme.spacing.lg))
                SectionHeader(title = stringResource(groupTitle))
                Spacer(Modifier.height(KitsuneTheme.spacing.xs))
                specs.forEach { spec ->
                    val slot = slots[spec.key]
                    val ref = slot?.ref.orEmpty()
                    // Recomposes when the catalog arrives, so raw ids turn into display names.
                    val label = remember(ref, catalog) { viewModel.label(ref) }
                    KitsuneRow(
                        title = stringResource(spec.title),
                        subtitle = when {
                            ref.isBlank() && automatic[spec.key] != null ->
                                stringResource(R.string.models_automatic, viewModel.label(automatic.getValue(spec.key)))
                            ref.isBlank() -> stringResource(R.string.models_not_set)
                            slot?.inherited == true -> stringResource(R.string.models_inherited, label)
                            else -> label
                        },
                        meta = stringResource(spec.hint),
                        onClick = { actionsFor = spec },
                        modifier = Modifier.padding(bottom = KitsuneTheme.spacing.sm)
                    )
                }
            }
        }
    }

    actionsFor?.let { spec ->
        KitsuneActionSheet(
            title = stringResource(spec.title),
            onDismiss = { actionsFor = null },
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.models_action_choose),
                    icon = Icons.Outlined.SwapHoriz,
                    onClick = { picking = spec }
                ),
                SheetAction(
                    label = stringResource(R.string.models_action_reset),
                    detail = stringResource(R.string.models_action_reset_detail),
                    icon = Icons.Outlined.Refresh,
                    enabled = slots[spec.key]?.inherited == false,
                    onClick = { viewModel.reset(spec.key) }
                )
            )
        )
    }

    picking?.let { spec ->
        ModelPickerDialog(
            capability = spec.capability,
            title = stringResource(spec.title),
            selectedModelId = slots[spec.key]?.ref,
            onDismiss = { picking = null },
            onModelSelected = { ref -> viewModel.select(spec.key, ref) }
        )
    }
}
