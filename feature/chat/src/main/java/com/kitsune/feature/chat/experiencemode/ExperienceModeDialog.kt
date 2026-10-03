package com.kitsune.feature.chat.experiencemode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsune.core.data.local.entities.IntensityMode
import com.kitsune.core.data.local.entities.InvolvementMode
import com.kitsune.core.data.local.entities.NarrativeRhythmMode
import com.kitsune.core.data.local.entities.NarrationBalanceMode
import com.kitsune.core.data.local.entities.ReplyLengthMode
import com.kitsune.core.data.local.entities.VoiceMode
import com.kitsune.core.data.local.entities.StoryPaceMode
import com.kitsune.core.data.local.entities.ToneMode
import com.kitsune.core.data.local.entities.UniverseExperienceMode
import com.kitsune.feature.chat.R

/**
 * "Modes d'expérience": per-chat narrative directives translated into system-prompt sentences by
 * `ChatStyleContract`, which renders them into the style contract sent as the last turn before
 * generation. Every category defaults to DEFAULT (no directive).
 * [allowMatureModes] gates the two options that only make sense for a persona already tagged
 * NSFW/DARK (Dark romance tone, Intense/mature intensity) — kept selectable regardless for
 * ensemble/universe chats, which have no per-cast maturity tag to check against.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExperienceModeDialog(
    storyPaceMode: StoryPaceMode,
    toneMode: ToneMode,
    involvementMode: InvolvementMode,
    narrativeRhythmMode: NarrativeRhythmMode,
    universeMode: UniverseExperienceMode,
    intensityMode: IntensityMode,
    replyLength: ReplyLengthMode,
    narrationBalance: NarrationBalanceMode,
    voiceMode: VoiceMode,
    /** Style packs this user owns and may apply to this conversation, as `id to displayName`. Empty
     *  when none is owned, in which case the section is not shown at all. */
    stylePacks: List<Pair<String, String>>,
    selectedStylePackId: String,
    allowMatureModes: Boolean,
    storyTimeAnchor: String,
    customDirective: String,
    onStoryPaceModeSelected: (StoryPaceMode) -> Unit,
    onToneModeSelected: (ToneMode) -> Unit,
    onInvolvementModeSelected: (InvolvementMode) -> Unit,
    onNarrativeRhythmModeSelected: (NarrativeRhythmMode) -> Unit,
    onUniverseModeSelected: (UniverseExperienceMode) -> Unit,
    onIntensityModeSelected: (IntensityMode) -> Unit,
    onReplyLengthSelected: (ReplyLengthMode) -> Unit,
    onNarrationBalanceSelected: (NarrationBalanceMode) -> Unit,
    onVoiceModeSelected: (VoiceMode) -> Unit,
    onStylePackSelected: (String) -> Unit,
    onStoryTimeAnchorChanged: (String) -> Unit,
    onCustomDirectiveChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val toneOptions = ToneMode.entries.filter { it != ToneMode.DARK_PSYCHOLOGICAL || allowMatureModes }
    val intensityOptions = IntensityMode.entries.filter { it != IntensityMode.INTENSE_MATURE || allowMatureModes }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.experience_modes_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.experience_modes_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = storyTimeAnchor,
                    onValueChange = onStoryTimeAnchorChanged,
                    label = { Text(stringResource(R.string.story_time_anchor_label)) },
                    supportingText = { Text(stringResource(R.string.story_time_anchor_helper)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                ExperienceModeCategory(stringResource(R.string.experience_mode_category_story_pace)) {
                    StoryPaceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == storyPaceMode,
                            onClick = { onStoryPaceModeSelected(mode) },
                            label = { Text(storyPaceModeLabel(mode)) }
                        )
                    }
                }

                ExperienceModeCategory(stringResource(R.string.experience_mode_category_tone)) {
                    toneOptions.forEach { mode ->
                        FilterChip(
                            selected = mode == toneMode,
                            onClick = { onToneModeSelected(mode) },
                            label = { Text(toneModeLabel(mode)) }
                        )
                    }
                }

                ExperienceModeCategory(stringResource(R.string.experience_mode_category_involvement)) {
                    InvolvementMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == involvementMode,
                            onClick = { onInvolvementModeSelected(mode) },
                            label = { Text(involvementModeLabel(mode)) }
                        )
                    }
                }

                ExperienceModeCategory(stringResource(R.string.experience_mode_category_rhythm)) {
                    NarrativeRhythmMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == narrativeRhythmMode,
                            onClick = { onNarrativeRhythmModeSelected(mode) },
                            label = { Text(narrativeRhythmModeLabel(mode)) }
                        )
                    }
                }

                ExperienceModeCategory(stringResource(R.string.experience_mode_category_universe)) {
                    UniverseExperienceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == universeMode,
                            onClick = { onUniverseModeSelected(mode) },
                            label = { Text(universeModeLabel(mode)) }
                        )
                    }
                }

                ExperienceModeCategory(
                    title = stringResource(R.string.experience_mode_category_intensity)
                ) {
                    intensityOptions.forEach { mode ->
                        FilterChip(
                            selected = mode == intensityMode,
                            onClick = { onIntensityModeSelected(mode) },
                            label = { Text(intensityModeLabel(mode)) }
                        )
                    }
                }

                // Reply shape (2026-08-23). Placed after the six narrative modes because these are
                // about the surface of a reply rather than about what happens in it — but they are
                // what a reader notices first, and none of the six could express them.
                ExperienceModeCategory(stringResource(R.string.experience_mode_category_length)) {
                    ReplyLengthMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == replyLength,
                            onClick = { onReplyLengthSelected(mode) },
                            label = { Text(replyLengthLabel(mode)) }
                        )
                    }
                }

                ExperienceModeCategory(stringResource(R.string.experience_mode_category_balance)) {
                    NarrationBalanceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == narrationBalance,
                            onClick = { onNarrationBalanceSelected(mode) },
                            label = { Text(narrationBalanceLabel(mode)) }
                        )
                    }
                }

                ExperienceModeCategory(stringResource(R.string.experience_mode_category_voice)) {
                    VoiceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == voiceMode,
                            onClick = { onVoiceModeSelected(mode) },
                            label = { Text(voiceModeLabel(mode)) }
                        )
                    }
                }

                // Per-conversation since 2026-08-23. The pack replaces the default prose register
                // rather than stacking on it, so exactly one can be active — which is also why owning
                // the bundle now means choosing among four rather than receiving all four at once.
                if (stylePacks.isNotEmpty()) {
                    ExperienceModeCategory(stringResource(R.string.experience_mode_category_style_pack)) {
                        FilterChip(
                            selected = selectedStylePackId.isBlank(),
                            onClick = { onStylePackSelected("") },
                            label = { Text(stringResource(R.string.style_pack_none)) }
                        )
                        stylePacks.forEach { (id, name) ->
                            FilterChip(
                                selected = id == selectedStylePackId,
                                onClick = { onStylePackSelected(id) },
                                label = { Text(name) }
                            )
                        }
                    }
                }

                // Last, and deliberately open-ended: the six enums above only cover a fixed set of
                // intentions. Anything else the user wants goes here, and outranks every preset.
                Text(
                    text = stringResource(R.string.experience_mode_category_custom),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
                OutlinedTextField(
                    value = customDirective,
                    onValueChange = onCustomDirectiveChanged,
                    placeholder = { Text(stringResource(R.string.experience_mode_custom_placeholder)) },
                    supportingText = { Text(stringResource(R.string.experience_mode_custom_helper)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExperienceModeCategory(
    title: String,
    isLast: Boolean = false,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
    if (!isLast) {
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun storyPaceModeLabel(mode: StoryPaceMode): String = stringResource(
    when (mode) {
        StoryPaceMode.DEFAULT -> R.string.experience_mode_default
        StoryPaceMode.SLICE_OF_LIFE -> R.string.experience_mode_story_pace_slice_of_life
        StoryPaceMode.NEW_ADVENTURE -> R.string.experience_mode_story_pace_new_adventure
        StoryPaceMode.SAGA -> R.string.experience_mode_story_pace_saga
        StoryPaceMode.EPISODIC -> R.string.experience_mode_story_pace_episodic
    }
)

@Composable
private fun toneModeLabel(mode: ToneMode): String = stringResource(
    when (mode) {
        ToneMode.DEFAULT -> R.string.experience_mode_default
        ToneMode.SOFT_ROMANTIC -> R.string.experience_mode_tone_soft_romantic
        ToneMode.DRAMATIC_ROMANCE -> R.string.experience_mode_tone_dramatic_romance
        ToneMode.DARK_PSYCHOLOGICAL -> R.string.experience_mode_tone_dark_psychological
        ToneMode.COMEDY -> R.string.experience_mode_tone_comedy
    }
)

@Composable
private fun involvementModeLabel(mode: InvolvementMode): String = stringResource(
    when (mode) {
        InvolvementMode.DEFAULT -> R.string.experience_mode_default
        InvolvementMode.READER -> R.string.experience_mode_involvement_reader
        InvolvementMode.CO_AUTHOR -> R.string.experience_mode_involvement_co_author
        InvolvementMode.IMMERSIVE_RP -> R.string.experience_mode_involvement_immersive_rp
    }
)

@Composable
private fun narrativeRhythmModeLabel(mode: NarrativeRhythmMode): String = stringResource(
    when (mode) {
        NarrativeRhythmMode.DEFAULT -> R.string.experience_mode_default
        NarrativeRhythmMode.SLOW_BURN -> R.string.experience_mode_rhythm_slow_burn
        NarrativeRhythmMode.FAST_PACED -> R.string.experience_mode_rhythm_fast_paced
        NarrativeRhythmMode.CONTEMPLATIVE -> R.string.experience_mode_rhythm_contemplative
    }
)

@Composable
private fun universeModeLabel(mode: UniverseExperienceMode): String = stringResource(
    when (mode) {
        UniverseExperienceMode.DEFAULT -> R.string.experience_mode_default
        UniverseExperienceMode.FIXED_SETTING -> R.string.experience_mode_universe_fixed_setting
        UniverseExperienceMode.MULTIVERSE -> R.string.experience_mode_universe_multiverse
        UniverseExperienceMode.CAMPAIGN -> R.string.experience_mode_universe_campaign
    }
)

@Composable
private fun intensityModeLabel(mode: IntensityMode): String = stringResource(
    when (mode) {
        IntensityMode.DEFAULT -> R.string.experience_mode_default
        IntensityMode.SOFT_SUGGESTIVE -> R.string.experience_mode_intensity_soft_suggestive
        IntensityMode.INTENSE_MATURE -> R.string.experience_mode_intensity_intense_mature
    }
)

@Composable
private fun replyLengthLabel(mode: ReplyLengthMode): String = stringResource(
    when (mode) {
        ReplyLengthMode.DEFAULT -> R.string.experience_mode_default
        ReplyLengthMode.BRIEF -> R.string.reply_length_brief
        ReplyLengthMode.MEDIUM -> R.string.reply_length_medium
        ReplyLengthMode.LONG -> R.string.reply_length_long
        ReplyLengthMode.UNBOUNDED -> R.string.reply_length_unbounded
    }
)

@Composable
private fun narrationBalanceLabel(mode: NarrationBalanceMode): String = stringResource(
    when (mode) {
        NarrationBalanceMode.DEFAULT -> R.string.experience_mode_default
        NarrationBalanceMode.MOSTLY_DIALOGUE -> R.string.narration_balance_dialogue
        NarrationBalanceMode.BALANCED -> R.string.narration_balance_even
        NarrationBalanceMode.MOSTLY_NARRATION -> R.string.narration_balance_narration
    }
)

@Composable
private fun voiceModeLabel(mode: VoiceMode): String = stringResource(
    when (mode) {
        VoiceMode.DEFAULT -> R.string.experience_mode_default
        VoiceMode.SECOND_PRESENT -> R.string.voice_second_present
        VoiceMode.THIRD_PAST -> R.string.voice_third_past
        VoiceMode.THIRD_PRESENT -> R.string.voice_third_present
        VoiceMode.FIRST_PAST -> R.string.voice_first_past
    }
)
