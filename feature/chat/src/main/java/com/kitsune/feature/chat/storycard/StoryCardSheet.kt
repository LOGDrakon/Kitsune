package com.kitsune.feature.chat.storycard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsune.feature.chat.R
import com.kitsune.feature.chat.style.StoryCardPresets
import com.kitsune.feature.chat.style.StoryPreset

/**
 * The framing question, asked once before the first message — the "carte d'histoire" (2026-08-23).
 *
 * ## What it is fixing
 *
 * Opening a chat used to drop the user straight into the conversation, and every style setting lived
 * behind ⋮ → "Modes d'expérience" as nine separate chip rows. In that state `buildStyleContract`
 * produced nothing but its default prose register, so the app's most influential prompt slot — the
 * last turn before generation — sat empty for precisely the conversations nobody had configured,
 * which is nearly all of them. That is the single largest reason stories read alike.
 *
 * ## Why it can be skipped, and why skipping still applies a preset
 *
 * A mandatory setup screen in front of every new conversation is friction on the one action the whole
 * app exists for. Skipping is therefore one tap — but it applies a real preset deduced from the
 * persona's own tags rather than leaving the chat at all-DEFAULT, because an unsteered chat is the
 * defect, not a neutral choice.
 *
 * Presentation only: which presets exist, which are allowed, and what they mean live in
 * [StoryCardPresets], where they are unit-tested.
 */
@Composable
fun StoryCardSheet(
    presets: List<StoryPreset>,
    isGenerating: Boolean,
    onChoosePreset: (StoryPreset) -> Unit,
    onDescribe: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var description by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.story_card_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.story_card_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(20.dp))
            presets.forEach { preset ->
                PresetCard(
                    preset = preset,
                    enabled = !isGenerating,
                    onClick = { onChoosePreset(preset) }
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.story_card_describe_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                stringResource(R.string.story_card_describe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                enabled = !isGenerating,
                minLines = 3,
                placeholder = { Text(stringResource(R.string.story_card_describe_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                TextButton(onClick = onSkip, enabled = !isGenerating) {
                    Text(stringResource(R.string.story_card_skip))
                }
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                } else {
                    TextButton(
                        onClick = { onDescribe(description) },
                        enabled = description.isNotBlank()
                    ) {
                        Text(stringResource(R.string.story_card_generate))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: StoryPreset, enabled: Boolean, onClick: () -> Unit) {
    // A tone card carries its own words — written by the persona's author, and possibly shipped from
    // the marketplace — so there is no resource to look them up in. Built-in presets resolve their
    // labels from the id instead, which keeps the catalogue free of Android types.
    val (titleRes, bodyRes) = presetStrings(preset.id)
    val title = preset.displayName ?: stringResource(titleRes)
    // An authored card with no description shows none, rather than borrowing the blurb of whichever
    // built-in recipe it happens to be based on — that would describe a register the author edited.
    val body = if (preset.displayName != null) preset.displayDescription else stringResource(bodyRes)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (preset.displayName != null) {
                Text(
                    stringResource(R.string.story_card_authored_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

internal fun presetStrings(id: String): Pair<Int, Int> = when (id) {
    StoryCardPresets.SLOW_ROMANCE -> R.string.preset_slow_romance to R.string.preset_slow_romance_body
    StoryCardPresets.DARK_PASSION -> R.string.preset_dark_passion to R.string.preset_dark_passion_body
    StoryCardPresets.ADVENTURE -> R.string.preset_adventure to R.string.preset_adventure_body
    StoryCardPresets.LIGHT_COMEDY -> R.string.preset_light_comedy to R.string.preset_light_comedy_body
    StoryCardPresets.LONG_SAGA -> R.string.preset_long_saga to R.string.preset_long_saga_body
    StoryCardPresets.CHAMBER_PIECE -> R.string.preset_chamber_piece to R.string.preset_chamber_piece_body
    // An id with no label is a catalogue entry someone forgot to translate; showing the generic
    // labels keeps the card usable instead of crashing on a missing resource.
    else -> R.string.preset_slow_romance to R.string.preset_slow_romance_body
}
