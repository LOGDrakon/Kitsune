package com.kitsune.feature.settings.generation

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.KitsuneNotice
import com.kitsune.core.designsystem.component.KitsunePage
import com.kitsune.core.designsystem.component.KitsuneRow
import com.kitsune.core.designsystem.component.KitsuneSegmented
import com.kitsune.core.designsystem.component.PageTitle
import com.kitsune.core.designsystem.component.SectionHeader
import com.kitsune.core.network.preferences.GenerationPreferences
import com.kitsune.core.network.preferences.MemoryDepth
import com.kitsune.feature.settings.R
import kotlin.math.roundToInt

private val PRESETS = listOf(MemoryDepth.LIGHT, MemoryDepth.BALANCED, MemoryDepth.EXTENDED)

/**
 * Memory and reply length — what used to be the paid "Pro" toggle, now a setting like any other.
 *
 * Two levels on one page: three presets for anyone who just wants it to work, and behind
 * "Réglages avancés" the numbers those presets stand for. Touching a number switches to "Personnalisé"
 * on its own, so the page never claims a preset that is no longer true.
 */
@Composable
fun GenerationScreen(
    onBack: () -> Unit,
    viewModel: GenerationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var advanced by rememberSaveable { mutableStateOf(state.depth == MemoryDepth.CUSTOM) }

    KitsunePage(title = stringResource(R.string.generation_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KitsuneTheme.spacing.gutter)
                .padding(bottom = KitsuneTheme.spacing.scrollBottom)
        ) {
            PageTitle(text = stringResource(R.string.generation_title))
            Text(
                stringResource(R.string.generation_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = KitsuneTheme.colors.textSecondary
            )

            Spacer(Modifier.height(KitsuneTheme.spacing.lg))
            SectionHeader(title = stringResource(R.string.generation_memory_section))
            Spacer(Modifier.height(KitsuneTheme.spacing.xs))
            KitsuneSegmented(
                options = PRESETS.map { stringResource(it.labelRes()) },
                // -1 while custom: no preset is highlighted, which is exactly the truth.
                selectedIndex = PRESETS.indexOf(state.depth),
                onSelect = { viewModel.setDepth(PRESETS[it]) }
            )
            Text(
                stringResource(
                    if (state.depth == MemoryDepth.CUSTOM) R.string.generation_depth_custom_hint
                    else state.depth.hintRes()
                ),
                style = KitsuneTheme.type.meta,
                color = KitsuneTheme.colors.textDim,
                modifier = Modifier.padding(top = KitsuneTheme.spacing.sm)
            )
            state.chatModelContext?.let { context ->
                KitsuneNotice(
                    text = stringResource(R.string.generation_model_context, context / 1000),
                    icon = Icons.Filled.Info,
                    modifier = Modifier.padding(top = KitsuneTheme.spacing.md)
                )
            }

            Spacer(Modifier.height(KitsuneTheme.spacing.lg))
            SectionHeader(title = stringResource(R.string.generation_writing_section))
            KitsuneRow(
                title = stringResource(R.string.generation_craft_label),
                meta = stringResource(R.string.generation_craft_hint),
                card = false,
                trailing = { Switch(checked = state.enhancedCraft, onCheckedChange = viewModel::setEnhancedCraft) },
                onClick = { viewModel.setEnhancedCraft(!state.enhancedCraft) }
            )

            Spacer(Modifier.height(KitsuneTheme.spacing.lg))
            KitsuneRow(
                title = stringResource(R.string.generation_advanced),
                meta = stringResource(R.string.generation_advanced_hint),
                card = false,
                trailing = { Switch(checked = advanced, onCheckedChange = { advanced = it }) },
                onClick = { advanced = !advanced }
            )
            if (advanced) {
                NumberSlider(
                    labelRes = R.string.generation_raw_window_label,
                    hint = stringResource(R.string.generation_raw_window_hint),
                    value = state.rawWindow,
                    range = GenerationPreferences.RAW_WINDOW_RANGE,
                    step = 2,
                    onChange = viewModel::setRawWindow
                )
                NumberSlider(
                    labelRes = R.string.generation_lore_label,
                    hint = stringResource(R.string.generation_lore_hint),
                    value = state.loreEntries,
                    range = GenerationPreferences.LORE_ENTRIES_RANGE,
                    step = 1,
                    onChange = viewModel::setLoreEntries
                )
                val choices = GenerationPreferences.MAX_REPLY_TOKENS_CHOICES
                Text(
                    stringResource(R.string.generation_max_tokens_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = KitsuneTheme.colors.text,
                    modifier = Modifier.padding(top = KitsuneTheme.spacing.lg, bottom = KitsuneTheme.spacing.xs)
                )
                KitsuneSegmented(
                    options = choices.map { "%,d".format(it).replace(',', ' ') },
                    selectedIndex = choices.indexOf(state.maxReplyTokens),
                    onSelect = { viewModel.setMaxReplyTokens(choices[it]) }
                )
                Text(
                    stringResource(R.string.generation_max_tokens_hint),
                    style = KitsuneTheme.type.meta,
                    color = KitsuneTheme.colors.textDim,
                    modifier = Modifier.padding(top = KitsuneTheme.spacing.sm)
                )
            }
        }
    }
}

/** A slider over whole numbers that only commits when the finger lifts — each commit rewrites the
 * memory settings, which is cheap but pointless to do sixty times a second. */
@Composable
private fun NumberSlider(
    @StringRes labelRes: Int,
    hint: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit
) {
    var dragging by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(top = KitsuneTheme.spacing.lg)) {
        Text(
            stringResource(labelRes, dragging.roundToInt()),
            style = MaterialTheme.typography.bodyLarge,
            color = KitsuneTheme.colors.text
        )
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onChange(dragging.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
        )
        Text(hint, style = KitsuneTheme.type.meta, color = KitsuneTheme.colors.textDim)
    }
}

private fun MemoryDepth.labelRes() = when (this) {
    MemoryDepth.LIGHT -> R.string.generation_depth_light
    MemoryDepth.BALANCED -> R.string.generation_depth_balanced
    MemoryDepth.EXTENDED -> R.string.generation_depth_extended
    MemoryDepth.CUSTOM -> R.string.generation_depth_custom
}

private fun MemoryDepth.hintRes() = when (this) {
    MemoryDepth.LIGHT -> R.string.generation_depth_light_hint
    MemoryDepth.BALANCED -> R.string.generation_depth_balanced_hint
    MemoryDepth.EXTENDED -> R.string.generation_depth_extended_hint
    MemoryDepth.CUSTOM -> R.string.generation_depth_custom_hint
}
