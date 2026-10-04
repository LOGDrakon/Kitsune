package com.kitsune.feature.chat.storysettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.KitsuneFilterChip
import com.kitsune.core.designsystem.component.KitsuneRow
import com.kitsune.core.designsystem.component.KitsuneSegmented
import com.kitsune.core.designsystem.component.KitsuneSheet
import com.kitsune.core.designsystem.component.SectionHeader
import com.kitsune.core.models.ModelCapabilityFilter
import com.kitsune.core.models.ModelPickerDialog
import com.kitsune.core.network.preferences.MemoryDepth
import com.kitsune.core.network.provider.ModelRef
import com.kitsune.feature.chat.R
import com.kitsune.feature.chat.storycard.presetStrings
import com.kitsune.feature.chat.style.StoryPreset

private val MEMORY_CHOICES: List<MemoryDepth?> = listOf(null, MemoryDepth.LIGHT, MemoryDepth.BALANCED, MemoryDepth.EXTENDED)
private val LENGTH_CHOICES: List<Int?> = listOf(null, 2048, 4096, 6144, 8192)

/**
 * Everything about *this* story, in one place, in two levels.
 *
 * Simple level, on this sheet: switch to another tone in one tap, and give the story its own model,
 * memory and reply length — each with a "Par défaut" choice that follows the global settings, so a
 * story only diverges where the user asked it to. Advanced level, one row away: the detailed
 * experience modes (nine axes and a free directive), unchanged.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorySettingsSheet(
    chat: ChatEntity,
    presets: List<StoryPreset>,
    memoryDepth: MemoryDepth?,
    globalModelLabel: String,
    onDismiss: () -> Unit,
    onSwitchPreset: (StoryPreset) -> Unit,
    onSetModel: (String?) -> Unit,
    onSetMemory: (MemoryDepth?) -> Unit,
    onSetMaxTokens: (Int?) -> Unit,
    onOpenDetailedModes: () -> Unit
) {
    var pickingModel by remember { mutableStateOf(false) }
    if (pickingModel) {
        ModelPickerDialog(
            capability = ModelCapabilityFilter.CHAT,
            title = stringResource(R.string.story_settings_model),
            selectedModelId = chat.chatModelRef,
            onDismiss = { pickingModel = false },
            onModelSelected = { ref ->
                onSetModel(ref)
                pickingModel = false
            }
        )
    }

    KitsuneSheet(onDismiss = onDismiss, title = stringResource(R.string.story_settings_title)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader(title = stringResource(R.string.story_settings_tone))
            Text(
                stringResource(R.string.story_settings_tone_hint),
                style = KitsuneTheme.type.meta,
                color = KitsuneTheme.colors.textDim
            )
            Spacer(Modifier.height(KitsuneTheme.spacing.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.sm)
            ) {
                presets.forEach { preset ->
                    KitsuneFilterChip(
                        text = preset.displayName ?: stringResource(presetStrings(preset.id).first),
                        selected = preset.displayName == null && preset.id == chat.storyPresetId,
                        onClick = { onSwitchPreset(preset) }
                    )
                }
            }

            Spacer(Modifier.height(KitsuneTheme.spacing.lg))
            SectionHeader(title = stringResource(R.string.story_settings_model))
            KitsuneRow(
                title = chat.chatModelRef?.let { ModelRef.parse(it).second }
                    ?: stringResource(R.string.story_settings_default_value, globalModelLabel),
                meta = stringResource(R.string.story_settings_model_hint),
                card = false,
                onClick = { pickingModel = true },
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
            )
            if (chat.chatModelRef != null) {
                KitsuneRow(
                    title = stringResource(R.string.story_settings_model_reset),
                    card = false,
                    onClick = { onSetModel(null) }
                )
            }

            Spacer(Modifier.height(KitsuneTheme.spacing.lg))
            SectionHeader(title = stringResource(R.string.story_settings_memory))
            KitsuneSegmented(
                options = MEMORY_CHOICES.map { stringResource(memoryLabel(it)) },
                selectedIndex = MEMORY_CHOICES.indexOf(memoryDepth),
                onSelect = { onSetMemory(MEMORY_CHOICES[it]) }
            )

            Spacer(Modifier.height(KitsuneTheme.spacing.lg))
            SectionHeader(title = stringResource(R.string.story_settings_length))
            KitsuneSegmented(
                options = LENGTH_CHOICES.map { tokens ->
                    if (tokens == null) stringResource(R.string.story_settings_default) else "${tokens / 1024}k"
                },
                selectedIndex = LENGTH_CHOICES.indexOf(chat.maxReplyTokens).coerceAtLeast(0),
                onSelect = { onSetMaxTokens(LENGTH_CHOICES[it]) }
            )
            Text(
                stringResource(R.string.story_settings_defaults_hint),
                style = KitsuneTheme.type.meta,
                color = KitsuneTheme.colors.textDim,
                modifier = Modifier.padding(top = KitsuneTheme.spacing.sm)
            )

            Spacer(Modifier.height(KitsuneTheme.spacing.lg))
            KitsuneRow(
                title = stringResource(R.string.story_settings_detailed),
                meta = stringResource(R.string.story_settings_detailed_hint),
                card = false,
                onClick = onOpenDetailedModes,
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
            )
        }
    }
}

private fun memoryLabel(depth: MemoryDepth?) = when (depth) {
    null -> R.string.story_settings_default
    MemoryDepth.LIGHT -> R.string.story_settings_memory_light
    MemoryDepth.BALANCED -> R.string.story_settings_memory_balanced
    MemoryDepth.EXTENDED, MemoryDepth.CUSTOM -> R.string.story_settings_memory_extended
}
