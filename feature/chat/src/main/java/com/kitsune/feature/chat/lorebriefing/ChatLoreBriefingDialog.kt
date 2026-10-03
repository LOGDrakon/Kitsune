package com.kitsune.feature.chat.lorebriefing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.feature.chat.R

@Composable
fun ChatLoreBriefingDialog(
    loreEntries: List<LoreEntryEntity>,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    val grouped = loreEntries.groupBy { it.entryType }.toSortedMap(compareBy { it.ordinal })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.briefing_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.briefing_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (loreEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.briefing_no_lore_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    grouped.forEach { (type, entries) ->
                        Text(
                            text = loreEntryTypeLabel(type),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        entries.forEachIndexed { index, entry ->
                            Text(
                                text = "${entry.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (entry.summary.isNotBlank()) {
                                Text(
                                    text = entry.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            if (index < entries.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onStart) {
                Text(stringResource(R.string.briefing_start_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.briefing_replay_later_button))
            }
        }
    )
}

@Composable
private fun loreEntryTypeLabel(type: LoreEntryType): String = when (type) {
    LoreEntryType.CHARACTER -> stringResource(R.string.lore_type_character)
    LoreEntryType.LOCATION -> stringResource(R.string.lore_type_location)
    LoreEntryType.FACTION -> stringResource(R.string.lore_type_faction)
    LoreEntryType.EVENT -> stringResource(R.string.lore_type_event)
    LoreEntryType.ITEM -> stringResource(R.string.lore_type_item)
    LoreEntryType.THREAD -> stringResource(R.string.lore_type_thread)
}
