package com.kitsune.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Shared "quick-generate with AI" block reused by universe creation, every add-entity dialog
 * (faction/location/NPC), and the in-chat "add a character to the scene" dialog: an optional
 * free-text description plus a generate button that fills the rest of the form, which the user
 * can still edit afterwards. Leaving the description blank is supported by design — the
 * underlying use case falls back to "invent something yourself". Lives in `core:designsystem`
 * (moved from `feature:universe`) so `feature:chat` can reuse it without a forbidden
 * feature→feature dependency.
 */
@Composable
fun AiQuickGenerateSection(
    description: String,
    onDescriptionChange: (String) -> Unit,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.designsystem_ai_quick_generate_label)) },
            placeholder = { Text(stringResource(R.string.designsystem_ai_quick_generate_placeholder)) },
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onGenerate,
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Row {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.designsystem_ai_quick_generate_button))
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    }
}
