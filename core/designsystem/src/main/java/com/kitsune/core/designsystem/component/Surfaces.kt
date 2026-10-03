package com.kitsune.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
 * Containers and list rows — the two shapes almost every screen in the app is made of.
 *
 * The rule v2 holds to, and v1 did not: **a card never nests inside a card.** Depth is one level.
 * Where v1 put a `Card` inside a `Card` inside an `ElevatedCard` (persona detail, universe detail),
 * v2 uses a [KitsuneSection] — a titled block on the page ground — and only the leaves are cards.
 */

/**
 * The standard container: a flat surface one step above the page, hairline border, [KitsuneShapes.md]
 * radius. No shadow — on a near-black ground a shadow is invisible and only costs a redraw.
 */
@Composable
fun KitsuneCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    shape: Shape = KitsuneTheme.shape.md,
    color: Color? = null,
    contentPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = KitsuneTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = when {
            pressed && onClick != null -> colors.surfaceVariant
            else -> color ?: colors.surface
        },
        label = "cardBg"
    )
    val border = BorderStroke(1.dp, if (selected) colors.accent else colors.outline)
    val inner: @Composable () -> Unit = {
        Column(
            modifier = if (contentPadding) Modifier.padding(KitsuneTheme.spacing.lg) else Modifier,
            content = content
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = container,
            contentColor = colors.text,
            border = border,
            interactionSource = interaction,
            modifier = modifier,
            content = inner
        )
    } else {
        Surface(
            shape = shape,
            color = container,
            contentColor = colors.text,
            border = border,
            modifier = modifier,
            content = inner
        )
    }
}

/**
 * A single list row: optional leading visual, title, optional subtitle and metadata, optional
 * trailing slot. This one component replaces the dozen bespoke `ListItem`/`Row` variants v1 grew,
 * and is the reason unrelated lists (stories, personas, settings, marketplace) now scan alike.
 */
@Composable
fun KitsuneRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    /** Marks the row as having something new — a single accent dot, no badge count, no colour block. */
    highlighted: Boolean = false,
    titleColor: Color? = null,
    /** `false` draws the row straight on the page ground instead of on a card surface. */
    card: Boolean = true
) {
    val colors = KitsuneTheme.colors
    val spacing = KitsuneTheme.spacing
    val body: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = spacing.lg, vertical = spacing.md)
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(spacing.md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor ?: colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (meta != null) {
                    Spacer(Modifier.height(spacing.sm))
                    Text(text = meta, style = KitsuneTheme.type.meta, color = colors.textDim, maxLines = 1)
                }
            }
            if (highlighted) {
                Spacer(Modifier.width(spacing.md))
                Box(Modifier.size(7.dp).background(colors.accent, CircleShape))
            }
            if (trailing != null) {
                Spacer(Modifier.width(spacing.sm))
                trailing()
            }
        }
    }
    if (card) {
        KitsuneCard(
            modifier = modifier,
            onClick = onClick,
            selected = selected,
            contentPadding = false
        ) { body() }
    } else {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val bg by animateColorAsState(
            if (pressed && onClick != null) colors.surface else Color.Transparent,
            label = "rowBg"
        )
        if (onClick != null) {
            Surface(
                onClick = onClick,
                color = bg,
                shape = KitsuneTheme.shape.md,
                interactionSource = interaction,
                modifier = modifier,
                content = { body() }
            )
        } else {
            Surface(color = Color.Transparent, modifier = modifier, content = { body() })
        }
    }
}

/**
 * A titled block on the page ground. Use instead of wrapping a group in a card: the title carries
 * the grouping, so the container does not have to.
 */
@Composable
fun KitsuneSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            SectionHeader(title = title, action = action, onAction = onAction)
            Spacer(Modifier.height(KitsuneTheme.spacing.md))
        }
        content()
    }
}

/**
 * Section title: a tracked-out all-caps eyebrow in [KitsuneColors.textDim], with an optional quiet
 * action on the right. Small and dim on purpose — it is a signpost, not content.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title.uppercase(),
            style = KitsuneTheme.type.eyebrow,
            color = KitsuneTheme.colors.textDim
        )
        if (action != null && onAction != null) {
            KitsuneQuietButton(text = action, onClick = onAction, accent = true)
        }
    }
}

/** Hairline separator. Inset to the gutter so it never touches the screen edge. */
@Composable
fun KitsuneDivider(modifier: Modifier = Modifier, inset: Boolean = false) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = if (inset) KitsuneTheme.spacing.lg else 0.dp)
            .height(1.dp)
            .background(KitsuneTheme.colors.outline)
    )
}

/**
 * A key/value line: label on the left in [KitsuneColors.textSecondary], value right-aligned in
 * [KitsuneColors.text]. Used by detail sheets, receipts and the pricing table.
 */
@Composable
fun KitsuneKeyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = KitsuneTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = KitsuneTheme.spacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = KitsuneTheme.type.numeric,
            color = valueColor ?: colors.text
        )
        if (trailing != null) trailing()
    }
}

/**
 * An inline notice: a tinted block with an icon and a line of copy. The app's only banner shape —
 * v1 had three (a `Card` with error colours, a bare coloured `Row`, and an `AlertDialog` used as a
 * banner), which is why warnings never looked like each other.
 */
@Composable
fun KitsuneNotice(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Info,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = KitsuneTheme.colors
    val (container, accent) = when (tone) {
        NoticeTone.Info -> colors.surfaceVariant to colors.textSecondary
        NoticeTone.Accent -> colors.accentContainer to colors.accent
        NoticeTone.Warn -> colors.warnContainer to colors.warn
        NoticeTone.Error -> colors.errorContainer to colors.error
        NoticeTone.Success -> colors.successContainer to colors.success
    }
    Surface(
        shape = KitsuneTheme.shape.md,
        color = container,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(KitsuneTheme.spacing.md)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(KitsuneTheme.spacing.md))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                modifier = Modifier.weight(1f)
            )
            if (action != null && onAction != null) {
                Spacer(Modifier.width(KitsuneTheme.spacing.sm))
                KitsuneQuietButton(text = action, onClick = onAction, accent = true)
            }
        }
    }
}

enum class NoticeTone { Info, Accent, Warn, Error, Success }
