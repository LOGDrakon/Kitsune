package com.kitsune.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kitsune.core.designsystem.KitsuneTheme

/**
 * Text input.
 *
 * Built on `BasicTextField` rather than Material's `OutlinedTextField` on purpose. The Material
 * field's floating label animation, 56dp minimum height and hard-coded 4dp-radius container are the
 * single most recognisable "stock Android form" tell, and its label-inside-the-border layout wastes
 * the vertical space a long persona description needs. Here the label sits above the field as a
 * small eyebrow, the container is the app's own surface and radius, and focus is communicated by the
 * border taking the accent — the same accent rule as everywhere else.
 */
@Composable
fun KitsuneTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    /** Helper text under the field. Replaced by [error] when that is non-null. */
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    password: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = KitsuneTheme.colors
    val spacing = KitsuneTheme.spacing
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            error != null -> colors.error
            focused -> colors.accent
            else -> colors.outline
        },
        label = "fieldBorder"
    )

    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label.uppercase(), style = KitsuneTheme.type.eyebrow, color = colors.textDim)
            Spacer(Modifier.height(spacing.sm))
        }
        Row(
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant, KitsuneTheme.shape.md)
                .border(1.dp, borderColor, KitsuneTheme.shape.md)
                .padding(horizontal = spacing.lg, vertical = spacing.md)
                .heightIn(min = if (singleLine) 24.dp else 24.dp * minLines)
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = colors.textDim,
                    modifier = Modifier.size(18.dp).padding(end = 0.dp)
                )
                Spacer(Modifier.width(spacing.md))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textFaint
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = { next ->
                        if (maxLength == null || next.length <= maxLength) onValueChange(next)
                    },
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.bodyLarge.copy(color = if (enabled) colors.text else colors.textFaint)
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = keyboardActions,
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(spacing.sm))
                trailing()
            }
        }
        // Helper, error and the character counter share one line so the field's height never jumps
        // as the user types — a counter appearing below a field is a layout shift mid-sentence.
        if (error != null || helper != null || maxLength != null) {
            Spacer(Modifier.height(spacing.xs))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xs)
            ) {
                Text(
                    text = error ?: helper.orEmpty(),
                    style = KitsuneTheme.type.meta,
                    color = if (error != null) colors.error else colors.textDim,
                    modifier = Modifier.weight(1f)
                )
                if (maxLength != null) {
                    Text(
                        text = "${value.length}/$maxLength",
                        style = KitsuneTheme.type.meta,
                        color = if (value.length >= maxLength) colors.warn else colors.textFaint
                    )
                }
            }
        }
    }
}

/** Search input: a pill, a magnifier, and a clear affordance that only exists when there is text. */
@Composable
fun KitsuneSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Rechercher"
) {
    val colors = KitsuneTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (focused) colors.accent else colors.outline,
        label = "searchBorder"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.surfaceVariant, KitsuneTheme.shape.pill)
            .border(1.dp, borderColor, KitsuneTheme.shape.pill)
            .padding(horizontal = KitsuneTheme.spacing.lg)
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(KitsuneTheme.spacing.md))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.textFaint)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            KitsuneIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Effacer",
                onClick = { onValueChange("") },
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
