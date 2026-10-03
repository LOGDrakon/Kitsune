package com.kitsune.core.designsystem.badges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A read-only pill for one creator badge — full color when [earned], grayed out with a small
 * lock glyph when not. Built on a plain [Surface]/[Row] (rather than `AssistChip`) so each badge's
 * color from [badgeDisplayInfo] can tint background/border/content directly. [awardedAtLabel] is a
 * pre-formatted date appended to the label when provided (e.g. "10 téléchargements · 12/03/2026"). */
@Composable
fun BadgeChip(
    badgeType: String,
    earned: Boolean,
    modifier: Modifier = Modifier,
    awardedAtLabel: String? = null
) {
    val info = badgeDisplayInfo(badgeType)
    val label = if (earned && awardedAtLabel != null) "${info.label} · $awardedAtLabel" else info.label
    val tint = if (earned) info.color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val background = if (earned) info.color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Surface(
        shape = RoundedCornerShape(50),
        color = background,
        border = BorderStroke(1.dp, tint.copy(alpha = if (earned) 0.5f else 0.35f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (earned) info.icon else Icons.Default.Lock,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
