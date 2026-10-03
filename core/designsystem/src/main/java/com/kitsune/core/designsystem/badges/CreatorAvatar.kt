package com.kitsune.core.designsystem.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fixed palette to pick a deterministic per-creator background color from — no photo/avatar
 * upload pipeline exists (`Users` has no avatar field), this is a placeholder by design, not a
 * loading state. See IDEAS.md for "avatar créateur uploadable" as a future, separate idea. */
private val avatarPalette = listOf(
    Color(0xFF5E35B1), Color(0xFF1E88E5), Color(0xFF00897B), Color(0xFF43A047),
    Color(0xFFF4511E), Color(0xFFD81B60), Color(0xFF6D4C41), Color(0xFF3949AB)
)

/** Circular initials avatar for a creator — same [name] always maps to the same color (hash-based
 * palette index), so a creator looks consistent across every screen/session. `null`/blank name
 * (shouldn't normally happen — every published creator has set a username) falls back to "?". */
@Composable
fun CreatorAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val trimmed = name?.trim().orEmpty()
    val initials = if (trimmed.isEmpty()) {
        "?"
    } else {
        trimmed.split(" ", "_", "-")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { trimmed.first().uppercase() }
    }
    val color = avatarPalette[(trimmed.ifEmpty { "?" }.hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) }) % avatarPalette.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.4f).sp
        )
    }
}
