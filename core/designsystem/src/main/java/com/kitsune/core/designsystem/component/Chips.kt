package com.kitsune.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kitsune.core.designsystem.KitsuneTheme

/**
 * Chips, filters and segmented controls.
 *
 * v1 leaned on chips for everything — tags, filters, status, counts, navigation — so a screen could
 * show fifteen pills of four different meanings at once, which is a lot of why it read as cluttered.
 * v2 splits them by role and gives each a distinct visual weight:
 *
 * - [KitsuneTag] — *read-only* metadata (a genre, a maturity rating). Never tappable, never coloured.
 * - [KitsuneFilterChip] — a filter the user toggles. Only these get the accent when active.
 * - [KitsuneSegmented] — mutually exclusive views of the same list. Replaces `TabRow`.
 */

/** Read-only metadata. Flat, dim, no border — it must not look like a control. */
@Composable
fun KitsuneTag(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: NoticeTone = NoticeTone.Info
) {
    val colors = KitsuneTheme.colors
    val (container, content) = when (tone) {
        NoticeTone.Info -> colors.surfaceVariant to colors.textSecondary
        NoticeTone.Accent -> colors.accentContainer to colors.accent
        NoticeTone.Warn -> colors.warnContainer to colors.warn
        NoticeTone.Error -> colors.errorContainer to colors.error
        NoticeTone.Success -> colors.successContainer to colors.success
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(container, KitsuneTheme.shape.sm)
            .padding(horizontal = KitsuneTheme.spacing.sm, vertical = 5.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** A toggleable filter. Accent border + accent text when on; hairline + dim when off. */
@Composable
fun KitsuneFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val colors = KitsuneTheme.colors
    val container by animateColorAsState(
        if (selected) colors.accentContainer else Color.Transparent,
        label = "chipBg"
    )
    val content by animateColorAsState(
        if (selected) colors.accent else colors.textSecondary,
        label = "chipFg"
    )
    Surface(
        onClick = onClick,
        shape = KitsuneTheme.shape.pill,
        color = container,
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.outline),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = KitsuneTheme.spacing.md)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}

/** A horizontally scrolling filter strip that bleeds to the screen edges. */
@Composable
fun KitsuneFilterRow(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    gutter: Boolean = true
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = if (gutter) KitsuneTheme.spacing.gutter else 0.dp
        )
    ) {
        items(options.size) { index ->
            val option = options[index]
            KitsuneFilterChip(
                text = option,
                selected = option == selected,
                onClick = { onSelect(option) }
            )
        }
    }
}

/**
 * Segmented control: two to four mutually exclusive views of the same content.
 *
 * Replaces Material's `TabRow`, whose full-width underline indicator visually cuts the screen in half
 * right where the content starts. A single inset track with a moving filled segment keeps the
 * grouping local to the control.
 */
@Composable
fun KitsuneSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KitsuneTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.surfaceVariant, KitsuneTheme.shape.pill)
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val container by animateColorAsState(
                if (selected) colors.surfaceBright else Color.Transparent,
                label = "segBg"
            )
            val content by animateColorAsState(
                if (selected) colors.text else colors.textDim,
                label = "segFg"
            )
            Surface(
                onClick = { onSelect(index) },
                shape = KitsuneTheme.shape.pill,
                color = container,
                modifier = Modifier.weight(1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * A count rendered as a dim numeral next to a label, e.g. "Personas 12".
 * Used instead of a Material `Badge`, which on this palette is a red dot that reads as an error.
 */
@Composable
fun KitsuneCount(label: String, count: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = KitsuneTheme.colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), style = KitsuneTheme.type.numeric, color = KitsuneTheme.colors.textDim)
    }
}

/** The single accent dot used everywhere something is new or live. */
@Composable
fun KitsuneDot(modifier: Modifier = Modifier, color: Color? = null) {
    Box(modifier.size(7.dp).background(color ?: KitsuneTheme.colors.accent, CircleShape))
}
