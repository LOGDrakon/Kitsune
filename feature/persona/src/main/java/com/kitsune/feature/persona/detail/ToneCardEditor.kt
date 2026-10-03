package com.kitsune.feature.persona.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsune.core.data.local.entities.IntensityMode
import com.kitsune.core.data.local.entities.InvolvementMode
import com.kitsune.core.data.local.entities.NarrationBalanceMode
import com.kitsune.core.data.local.entities.NarrativeRhythmMode
import com.kitsune.core.data.local.entities.ReplyLengthMode
import com.kitsune.core.data.local.entities.StoryPaceMode
import com.kitsune.core.data.local.entities.ToneCardEntity
import com.kitsune.core.data.local.entities.ToneMode
import com.kitsune.core.data.local.entities.UniverseExperienceMode
import com.kitsune.core.data.local.entities.VoiceMode
import com.kitsune.feature.persona.R

/**
 * Authoring dialog for a persona's own tone cards (2026-08-24).
 *
 * ## What a tone card is for
 *
 * The story card offers six built-in recipes that have to work for *any* character. A given persona
 * usually deserves more than one register of its own — the same rival can carry a slow-burn office
 * romance and a biting comedy — and re-deriving each from nine chip rows at the start of every
 * conversation is exactly the friction the story card exists to remove. A tone card freezes one such
 * register under a name, on the character sheet, and travels with the persona through export and the
 * marketplace.
 *
 * ## Why the labels are duplicated here
 *
 * The same mode labels exist in `feature:chat`'s experience-mode dialog. They are not shared because
 * there is nowhere to share them from: `core:designsystem` deliberately has no project dependencies,
 * so it cannot see the enums, and `feature → feature` is forbidden. Duplicating presentation strings
 * is the cheaper of the two prices — the enums themselves, which are the actual contract, stay
 * single-sourced in `core:data`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToneCardDialog(
    initial: ToneCardEntity?,
    allowMatureModes: Boolean,
    onDismiss: () -> Unit,
    onSave: (ToneCardDraft) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var directive by remember { mutableStateOf(initial?.directive.orEmpty()) }
    var storyPace by remember { mutableStateOf(initial?.storyPaceMode ?: StoryPaceMode.DEFAULT) }
    var tone by remember { mutableStateOf(initial?.toneMode ?: ToneMode.DEFAULT) }
    var involvement by remember { mutableStateOf(initial?.involvementMode ?: InvolvementMode.DEFAULT) }
    var rhythm by remember { mutableStateOf(initial?.narrativeRhythmMode ?: NarrativeRhythmMode.DEFAULT) }
    var universe by remember { mutableStateOf(initial?.universeMode ?: UniverseExperienceMode.DEFAULT) }
    var intensity by remember { mutableStateOf(initial?.intensityMode ?: IntensityMode.DEFAULT) }
    var replyLength by remember { mutableStateOf(initial?.replyLength ?: ReplyLengthMode.DEFAULT) }
    var balance by remember { mutableStateOf(initial?.narrationBalance ?: NarrationBalanceMode.DEFAULT) }
    var voice by remember { mutableStateOf(initial?.voiceMode ?: VoiceMode.DEFAULT) }

    // Same gate as the experience-mode dialog and the story card: authoring a tone card must not be a
    // way to hand a plainly-tagged persona a register it could not otherwise be given.
    val toneOptions = ToneMode.entries.filter { it != ToneMode.DARK_PSYCHOLOGICAL || allowMatureModes }
    val intensityOptions = IntensityMode.entries.filter { it != IntensityMode.INTENSE_MATURE || allowMatureModes }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tone_card_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.tone_card_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tone_card_name_label)) },
                    placeholder = { Text(stringResource(R.string.tone_card_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.tone_card_description_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2
                )

                ToneCardCategory(stringResource(R.string.tone_card_category_story_pace)) {
                    StoryPaceMode.entries.forEach { m ->
                        FilterChip(storyPace == m, { storyPace = m }, { Text(storyPaceLabel(m)) })
                    }
                }
                ToneCardCategory(stringResource(R.string.tone_card_category_tone)) {
                    toneOptions.forEach { m -> FilterChip(tone == m, { tone = m }, { Text(toneLabel(m)) }) }
                }
                ToneCardCategory(stringResource(R.string.tone_card_category_involvement)) {
                    InvolvementMode.entries.forEach { m ->
                        FilterChip(involvement == m, { involvement = m }, { Text(involvementLabel(m)) })
                    }
                }
                ToneCardCategory(stringResource(R.string.tone_card_category_rhythm)) {
                    NarrativeRhythmMode.entries.forEach { m ->
                        FilterChip(rhythm == m, { rhythm = m }, { Text(rhythmLabel(m)) })
                    }
                }
                ToneCardCategory(stringResource(R.string.tone_card_category_universe)) {
                    UniverseExperienceMode.entries.forEach { m ->
                        FilterChip(universe == m, { universe = m }, { Text(universeLabel(m)) })
                    }
                }
                ToneCardCategory(stringResource(R.string.tone_card_category_intensity)) {
                    intensityOptions.forEach { m ->
                        FilterChip(intensity == m, { intensity = m }, { Text(intensityLabel(m)) })
                    }
                }
                ToneCardCategory(stringResource(R.string.tone_card_category_length)) {
                    ReplyLengthMode.entries.forEach { m ->
                        FilterChip(replyLength == m, { replyLength = m }, { Text(replyLengthLabel(m)) })
                    }
                }
                ToneCardCategory(stringResource(R.string.tone_card_category_balance)) {
                    NarrationBalanceMode.entries.forEach { m ->
                        FilterChip(balance == m, { balance = m }, { Text(balanceLabel(m)) })
                    }
                }
                ToneCardCategory(stringResource(R.string.tone_card_category_voice)) {
                    VoiceMode.entries.forEach { m ->
                        FilterChip(voice == m, { voice = m }, { Text(voiceLabel(m)) })
                    }
                }

                Text(
                    stringResource(R.string.tone_card_category_directive),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
                OutlinedTextField(
                    value = directive,
                    onValueChange = { directive = it },
                    placeholder = { Text(stringResource(R.string.tone_card_directive_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        ToneCardDraft(
                            name = name.trim(),
                            description = description.trim(),
                            directive = directive.trim(),
                            storyPaceMode = storyPace,
                            toneMode = tone,
                            involvementMode = involvement,
                            narrativeRhythmMode = rhythm,
                            universeMode = universe,
                            intensityMode = intensity,
                            replyLength = replyLength,
                            narrationBalance = balance,
                            voiceMode = voice
                        )
                    )
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** What the dialog hands back — the entity minus its identity and scope, which the caller owns. */
data class ToneCardDraft(
    val name: String,
    val description: String,
    val directive: String,
    val storyPaceMode: StoryPaceMode,
    val toneMode: ToneMode,
    val involvementMode: InvolvementMode,
    val narrativeRhythmMode: NarrativeRhythmMode,
    val universeMode: UniverseExperienceMode,
    val intensityMode: IntensityMode,
    val replyLength: ReplyLengthMode,
    val narrationBalance: NarrationBalanceMode,
    val voiceMode: VoiceMode
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToneCardCategory(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
    FlowRow(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun storyPaceLabel(m: StoryPaceMode): String = stringResource(
    when (m) {
        StoryPaceMode.DEFAULT -> R.string.tone_card_default
        StoryPaceMode.SLICE_OF_LIFE -> R.string.tone_card_pace_slice_of_life
        StoryPaceMode.NEW_ADVENTURE -> R.string.tone_card_pace_new_adventure
        StoryPaceMode.SAGA -> R.string.tone_card_pace_saga
        StoryPaceMode.EPISODIC -> R.string.tone_card_pace_episodic
    }
)

@Composable
private fun toneLabel(m: ToneMode): String = stringResource(
    when (m) {
        ToneMode.DEFAULT -> R.string.tone_card_default
        ToneMode.SOFT_ROMANTIC -> R.string.tone_card_tone_soft_romantic
        ToneMode.DRAMATIC_ROMANCE -> R.string.tone_card_tone_dramatic_romance
        ToneMode.DARK_PSYCHOLOGICAL -> R.string.tone_card_tone_dark
        ToneMode.COMEDY -> R.string.tone_card_tone_comedy
    }
)

@Composable
private fun involvementLabel(m: InvolvementMode): String = stringResource(
    when (m) {
        InvolvementMode.DEFAULT -> R.string.tone_card_default
        InvolvementMode.READER -> R.string.tone_card_involvement_reader
        InvolvementMode.CO_AUTHOR -> R.string.tone_card_involvement_co_author
        InvolvementMode.IMMERSIVE_RP -> R.string.tone_card_involvement_immersive
    }
)

@Composable
private fun rhythmLabel(m: NarrativeRhythmMode): String = stringResource(
    when (m) {
        NarrativeRhythmMode.DEFAULT -> R.string.tone_card_default
        NarrativeRhythmMode.SLOW_BURN -> R.string.tone_card_rhythm_slow_burn
        NarrativeRhythmMode.FAST_PACED -> R.string.tone_card_rhythm_fast
        NarrativeRhythmMode.CONTEMPLATIVE -> R.string.tone_card_rhythm_contemplative
    }
)

@Composable
private fun universeLabel(m: UniverseExperienceMode): String = stringResource(
    when (m) {
        UniverseExperienceMode.DEFAULT -> R.string.tone_card_default
        UniverseExperienceMode.FIXED_SETTING -> R.string.tone_card_universe_fixed
        UniverseExperienceMode.MULTIVERSE -> R.string.tone_card_universe_multiverse
        UniverseExperienceMode.CAMPAIGN -> R.string.tone_card_universe_campaign
    }
)

@Composable
private fun intensityLabel(m: IntensityMode): String = stringResource(
    when (m) {
        IntensityMode.DEFAULT -> R.string.tone_card_default
        IntensityMode.SOFT_SUGGESTIVE -> R.string.tone_card_intensity_suggestive
        IntensityMode.INTENSE_MATURE -> R.string.tone_card_intensity_explicit
    }
)

@Composable
private fun replyLengthLabel(m: ReplyLengthMode): String = stringResource(
    when (m) {
        ReplyLengthMode.DEFAULT -> R.string.tone_card_default
        ReplyLengthMode.BRIEF -> R.string.tone_card_length_brief
        ReplyLengthMode.MEDIUM -> R.string.tone_card_length_medium
        ReplyLengthMode.LONG -> R.string.tone_card_length_long
        ReplyLengthMode.UNBOUNDED -> R.string.tone_card_length_unbounded
    }
)

@Composable
private fun balanceLabel(m: NarrationBalanceMode): String = stringResource(
    when (m) {
        NarrationBalanceMode.DEFAULT -> R.string.tone_card_default
        NarrationBalanceMode.MOSTLY_DIALOGUE -> R.string.tone_card_balance_dialogue
        NarrationBalanceMode.BALANCED -> R.string.tone_card_balance_even
        NarrationBalanceMode.MOSTLY_NARRATION -> R.string.tone_card_balance_narration
    }
)

@Composable
private fun voiceLabel(m: VoiceMode): String = stringResource(
    when (m) {
        VoiceMode.DEFAULT -> R.string.tone_card_default
        VoiceMode.SECOND_PRESENT -> R.string.tone_card_voice_second_present
        VoiceMode.THIRD_PAST -> R.string.tone_card_voice_third_past
        VoiceMode.THIRD_PRESENT -> R.string.tone_card_voice_third_present
        VoiceMode.FIRST_PAST -> R.string.tone_card_voice_first_past
    }
)
