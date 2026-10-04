package com.kitsune.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.kitsune.core.designsystem.KitsuneTheme

/**
 * A setting that is on or off: a title, an optional explanation, a switch.
 *
 * Unlike [KitsuneRow] (one line of title, one of meta — made for lists), the text here wraps: a setting
 * whose explanation is cut after six words is a setting nobody understands. The whole row toggles,
 * not only the switch.
 */
@Composable
fun KitsuneSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = KitsuneTheme.spacing.sm)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) KitsuneTheme.colors.text else KitsuneTheme.colors.textFaint
            )
            if (description != null) {
                Text(
                    description,
                    style = KitsuneTheme.type.meta,
                    color = KitsuneTheme.colors.textDim,
                    modifier = Modifier.padding(top = KitsuneTheme.spacing.xs)
                )
            }
        }
        Spacer(Modifier.width(KitsuneTheme.spacing.md))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
