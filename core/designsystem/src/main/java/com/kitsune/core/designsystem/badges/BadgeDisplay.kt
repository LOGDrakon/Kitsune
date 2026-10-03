package com.kitsune.core.designsystem.badges

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kitsune.core.designsystem.R

/** Presentation metadata for one creator badge. [badgeType] is the raw wire value (e.g.
 * "DOWNLOADS_10") rather than a shared enum type — this module deliberately doesn't depend on
 * `core:backend` (same convention as `AnnouncementDialog`'s `type: String`), so the mapping is a
 * plain `when` on the string instead of an imported enum. */
data class BadgeDisplayInfo(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

/** Colors mirror the internal admin panel's badge style map (`admin.html`) for visual continuity
 * between the admin tools and the app. Unrecognized [badgeType] values (e.g. a badge added
 * server-side after this app version shipped) fall back to a generic chip rather than crashing. */
@Composable
fun badgeDisplayInfo(badgeType: String): BadgeDisplayInfo = when (badgeType.uppercase()) {
    "DOWNLOADS_10" -> BadgeDisplayInfo(
        label = stringResource(R.string.designsystem_badge_downloads_10_label),
        description = stringResource(R.string.designsystem_badge_downloads_10_desc),
        icon = Icons.Default.Download,
        color = Color(0xFF1565C0)
    )
    "DOWNLOADS_50" -> BadgeDisplayInfo(
        label = stringResource(R.string.designsystem_badge_downloads_50_label),
        description = stringResource(R.string.designsystem_badge_downloads_50_desc),
        icon = Icons.Default.Download,
        color = Color(0xFF0D47A1)
    )
    "DOWNLOADS_100" -> BadgeDisplayInfo(
        label = stringResource(R.string.designsystem_badge_downloads_100_label),
        description = stringResource(R.string.designsystem_badge_downloads_100_desc),
        icon = Icons.Default.Download,
        color = Color(0xFF0D47A1)
    )
    "DOWNLOADS_500" -> BadgeDisplayInfo(
        label = stringResource(R.string.designsystem_badge_downloads_500_label),
        description = stringResource(R.string.designsystem_badge_downloads_500_desc),
        icon = Icons.Default.Download,
        color = Color(0xFF0D47A1)
    )
    "RATING_4PLUS" -> BadgeDisplayInfo(
        label = stringResource(R.string.designsystem_badge_rating_4plus_label),
        description = stringResource(R.string.designsystem_badge_rating_4plus_desc),
        icon = Icons.Default.Star,
        color = Color(0xFF2E7D32)
    )
    "RATING_5_WITH_10_REVIEWS" -> BadgeDisplayInfo(
        label = stringResource(R.string.designsystem_badge_rating_top_label),
        description = stringResource(R.string.designsystem_badge_rating_top_desc),
        icon = Icons.Default.StarRate,
        color = Color(0xFF1B5E20)
    )
    "PROLIFIC" -> BadgeDisplayInfo(
        label = stringResource(R.string.designsystem_badge_prolific_label),
        description = stringResource(R.string.designsystem_badge_prolific_desc),
        icon = Icons.Default.AutoAwesome,
        color = Color(0xFFE65100)
    )
    "PIONEER" -> BadgeDisplayInfo(
        label = stringResource(R.string.designsystem_badge_pioneer_label),
        description = stringResource(R.string.designsystem_badge_pioneer_desc),
        icon = Icons.Default.EmojiEvents,
        color = Color(0xFF6A1B9A)
    )
    else -> BadgeDisplayInfo(
        label = stringResource(R.string.designsystem_badge_unknown_label),
        description = stringResource(R.string.designsystem_badge_unknown_desc),
        icon = Icons.Default.Star,
        color = Color.Gray
    )
}
