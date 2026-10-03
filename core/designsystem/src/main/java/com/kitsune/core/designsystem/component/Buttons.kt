package com.kitsune.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsune.core.designsystem.KitsuneTheme

/**
 * The button set.
 *
 * v1 used stock `Button`/`OutlinedButton`/`TextButton` with ad-hoc `colors =` overrides at most call
 * sites, which is why no two screens agreed on what a primary action looked like. Here there are
 * exactly four kinds, ordered by how loud they are, and a screen should normally show **one**
 * [KitsuneButton] and nothing louder:
 *
 * | | use |
 * |---|---|
 * | [KitsuneButton] | the one action the screen exists for |
 * | [KitsuneSecondaryButton] | a real alternative to it |
 * | [KitsuneQuietButton] | dismiss, "later", inline navigation |
 * | [KitsuneDangerButton] | destructive confirmation only |
 *
 * All four share height, radius, label style and press feedback, so mixing them in one row still
 * looks composed.
 */

private val ButtonHeight = 48.dp
private val ButtonHeightCompact = 38.dp
private val IconGap = 8.dp

/** Filled accent. The loudest thing the design system offers. */
@Composable
fun KitsuneButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    fillWidth: Boolean = true
) {
    val colors = KitsuneTheme.colors
    ButtonShell(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        container = if (enabled) colors.accent else colors.surfaceVariant,
        content = if (enabled) colors.onAccent else colors.textFaint,
        border = null,
        compact = compact,
        fillWidth = fillWidth
    ) {
        ButtonContent(text = text, icon = icon, loading = loading, tint = if (enabled) colors.onAccent else colors.textFaint)
    }
}

/** Outlined. Same footprint as [KitsuneButton] so the two align when shown side by side. */
@Composable
fun KitsuneSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    fillWidth: Boolean = true
) {
    val colors = KitsuneTheme.colors
    val tint = if (enabled) colors.text else colors.textFaint
    ButtonShell(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        container = Color.Transparent,
        content = tint,
        border = BorderStroke(1.dp, if (enabled) colors.outlineStrong else colors.outline),
        compact = compact,
        fillWidth = fillWidth
    ) {
        ButtonContent(text = text, icon = icon, loading = loading, tint = tint)
    }
}

/** Text only. For anything that must not compete: "Plus tard", "Annuler", "Tout voir". */
@Composable
fun KitsuneQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    accent: Boolean = false,
    compact: Boolean = true,
    fillWidth: Boolean = false
) {
    val colors = KitsuneTheme.colors
    val tint = when {
        !enabled -> colors.textFaint
        accent -> colors.accent
        else -> colors.textSecondary
    }
    ButtonShell(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        container = Color.Transparent,
        content = tint,
        border = null,
        compact = compact,
        fillWidth = fillWidth
    ) {
        ButtonContent(text = text, icon = icon, loading = loading, tint = tint)
    }
}

/**
 * Destructive. Outlined in [KitsuneColors.error] rather than filled with it — a solid red slab reads
 * as an alarm the moment the screen opens, before the user has done anything wrong.
 */
@Composable
fun KitsuneDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    fillWidth: Boolean = true
) {
    val colors = KitsuneTheme.colors
    val tint = if (enabled) colors.error else colors.textFaint
    ButtonShell(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        container = if (enabled) colors.errorContainer else Color.Transparent,
        content = tint,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
        compact = compact,
        fillWidth = fillWidth
    ) {
        ButtonContent(text = text, icon = icon, loading = loading, tint = tint)
    }
}

/**
 * A bare tappable icon, 44dp target with a 20dp glyph. Used in top bars and row trailing slots.
 * Deliberately has no background at rest: a row of stock `IconButton`s with ripple containers is
 * most of what made v1's top bars look busy.
 */
@Composable
fun KitsuneIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    badged: Boolean = false
) {
    val colors = KitsuneTheme.colors
    val resolved = when {
        !enabled -> colors.textFaint
        tint != null -> tint
        else -> colors.textSecondary
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (pressed) colors.surfaceVariant else Color.Transparent,
        label = "iconButtonBg"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = bg,
        interactionSource = interaction,
        modifier = modifier.size(KitsuneTheme.spacing.touchTarget)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = resolved,
                modifier = Modifier.size(20.dp)
            )
            if (badged) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 11.dp, end = 11.dp)
                        .size(7.dp)
                        .background(colors.accent, CircleShape)
                )
            }
        }
    }
}

/**
 * The single floating action button.
 *
 * Extended (icon + label) rather than icon-only: v1's bare "+" FAB meant the primary creation
 * affordance was unlabelled on the app's most important screen.
 */
@Composable
fun KitsuneFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KitsuneTheme.colors
    Surface(
        onClick = onClick,
        shape = KitsuneTheme.shape.pill,
        color = colors.accent,
        shadowElevation = KitsuneTheme.elevation.floating,
        modifier = modifier.height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 22.dp)
        ) {
            Icon(icon, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(IconGap))
            Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onAccent)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Internals
// ---------------------------------------------------------------------------------------------

@Composable
private fun ButtonShell(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    content: Color,
    border: BorderStroke?,
    compact: Boolean,
    fillWidth: Boolean,
    shape: Shape = KitsuneTheme.shape.pill,
    inner: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Press feedback is a colour shift rather than a scale or ripple: it stays legible on a dark
    // ground and does not fight the accent.
    val bg by animateColorAsState(
        targetValue = if (pressed && enabled) container.copy(alpha = 0.78f) else container,
        label = "buttonBg"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = bg,
        contentColor = content,
        border = border,
        interactionSource = interaction,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(if (compact) ButtonHeightCompact else ButtonHeight)
            .defaultMinSize(minWidth = 64.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = if (compact) 14.dp else 20.dp)) {
            inner()
        }
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, loading: Boolean, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        when {
            loading -> {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = tint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(IconGap))
            }
            icon != null -> {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(IconGap))
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
