package com.kitsune.feature.marketplace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.backend.model.BadgeProgress
import com.kitsune.core.backend.model.CreatorProfile
import com.kitsune.core.designsystem.badges.BadgeChip
import com.kitsune.core.designsystem.badges.DownloadTierProgressBar
import com.kitsune.core.designsystem.badges.SingleThresholdProgressBar
import com.kitsune.core.designsystem.badges.StatChip

/** "Mes badges" — own badge collection + progress toward the next tier, reached from Settings →
 * Compte. Reuses the same aggregate profile endpoint as the public creator profile header
 * (`CreatorListingsScreen`'s `CreatorProfileHeader`), just called with one's own id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBadgesScreen(
    onBack: () -> Unit,
    viewModel: MyBadgesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_badges_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_desc_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is MyBadgesUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is MyBadgesUiState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.my_badges_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(32.dp)
                )
            }
            is MyBadgesUiState.Ready -> MyBadgesContent(state.profile, modifier = Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun MyBadgesContent(profile: CreatorProfile, modifier: Modifier = Modifier) {
    val badgesByType = profile.badges.associateBy { it.badgeType }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatChip(Icons.Default.Inventory, profile.listingCount.toString(), stringResource(R.string.stat_listings_label))
            StatChip(Icons.Default.Download, profile.totalDownloadCount.toString(), stringResource(R.string.stat_downloads_label))
            StatChip(Icons.Default.Star, String.format("%.1f", profile.averageRating), stringResource(R.string.stat_rating_label))
            StatChip(Icons.Default.Group, profile.followerCount.toString(), stringResource(R.string.stat_followers_label))
        }

        Spacer(Modifier.height(24.dp))

        Text(
            stringResource(R.string.my_badges_downloads_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        DownloadTierProgressBar(currentValue = profile.totalDownloadCount, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        FlowRowBadges(
            listOfNotNull(
                badgesByType["DOWNLOADS_10"],
                badgesByType["DOWNLOADS_50"],
                badgesByType["DOWNLOADS_100"],
                badgesByType["DOWNLOADS_500"]
            )
        )

        Spacer(Modifier.height(24.dp))

        Text(
            stringResource(R.string.my_badges_other_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        badgesByType["RATING_4PLUS"]?.let { badge ->
            SingleThresholdProgressBar(
                currentValue = badge.currentValue,
                targetValue = badge.targetValue,
                valueLabel = stringResource(R.string.my_badges_rating_progress_format, badge.currentValue, badge.targetValue),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            BadgeChip(badge.badgeType, earned = badge.awarded, awardedAtLabel = shortDate(badge.awardedAt))
            Spacer(Modifier.height(16.dp))
        }

        badgesByType["PROLIFIC"]?.let { badge ->
            SingleThresholdProgressBar(
                currentValue = badge.currentValue,
                targetValue = badge.targetValue,
                valueLabel = stringResource(R.string.my_badges_prolific_progress_format, badge.currentValue.toInt(), badge.targetValue.toInt()),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            BadgeChip(badge.badgeType, earned = badge.awarded, awardedAtLabel = shortDate(badge.awardedAt))
            Spacer(Modifier.height(16.dp))
        }

        FlowRowBadges(listOfNotNull(badgesByType["RATING_5_WITH_10_REVIEWS"]))

        badgesByType["PIONEER"]?.let { badge ->
            Spacer(Modifier.height(8.dp))
            BadgeChip(badge.badgeType, earned = badge.awarded, awardedAtLabel = shortDate(badge.awardedAt))
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.my_badges_pioneer_rank_format, profile.pioneerRank, profile.pioneerCutoff),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowBadges(badges: List<BadgeProgress>) {
    if (badges.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.forEach { badge ->
            BadgeChip(badge.badgeType, earned = badge.awarded, awardedAtLabel = shortDate(badge.awardedAt))
        }
    }
}

/** [awardedAt] is a full ISO-8601 instant from the backend — only the date portion is shown in a
 * chip, no dedicated date-formatting dependency needed for that. */
private fun shortDate(awardedAt: String?): String? = awardedAt?.substringBefore('T')?.takeIf { it.isNotBlank() }
