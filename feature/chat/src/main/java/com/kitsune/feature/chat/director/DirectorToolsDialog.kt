package com.kitsune.feature.chat.director

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsune.feature.chat.DirectorToolKind
import com.kitsune.feature.chat.R

@Composable
fun DirectorToolsDialog(
    onDismiss: () -> Unit,
    onToolSelected: (DirectorToolKind) -> Unit
) {
    val tools = listOf(
        DirectorToolKind.SCENE_CHANGE to stringResource(R.string.director_tool_scene_change),
        DirectorToolKind.PLOT_TWIST to stringResource(R.string.director_tool_plot_twist),
        DirectorToolKind.TIME_SKIP to stringResource(R.string.director_tool_time_skip),
        DirectorToolKind.RANDOM_INTERLUDE to stringResource(R.string.director_tool_random_interlude)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scene_tools_label)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.director_tools_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                tools.forEach { (kind, label) ->
                    TextButton(
                        onClick = { onToolSelected(kind) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
