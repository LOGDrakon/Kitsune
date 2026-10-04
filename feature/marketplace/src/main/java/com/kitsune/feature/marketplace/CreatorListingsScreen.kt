package com.kitsune.feature.marketplace

import com.kitsune.core.designsystem.component.KitsunePage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kitsune.core.backend.model.CreatorProfile
import com.kitsune.core.designsystem.badges.CreatorAvatar
import com.kitsune.core.designsystem.badges.StatChip

/** "Voir les annonces de ce créateur", opened from [MyFollowsScreen] and from tapping a creator's
 * name in [MarketplaceScreen]'s grid/detail screens — reuses [MarketplaceViewModel] (a fresh
 * instance, scoped to this screen's own back-stack entry, not the same one the main browse tab
 * holds) filtered to a single [creatorId] rather than a new, parallel loading/paging
 * implementation. Doubles as the creator's public profile page: a header (avatar, stats,
 * follow button) sits above their listing grid, backed by the same aggregate
 * `GET /marketplace/creators/{userId}/profile` endpoint. The title falls back to
 * the first loaded listing's `creatorName` while the profile call is still in flight (or if it
 * fails) rather than requiring the name as a nav argument, so this route only ever needs the id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorListingsScreen(
    creatorId: String,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    viewModel: MarketplaceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val creatorProfile by viewModel.creatorProfile.collectAsState()

    LaunchedEffect(creatorId) {
        viewModel.loadListings(MarketplaceFilter(creatorId = creatorId, sort = "recent"))
        viewModel.loadCreatorProfile(creatorId)
    }

    val creatorName = creatorProfile?.creatorName
        ?: (uiState as? MarketplaceUiState.Ready)?.listings?.firstOrNull()?.creatorName

    // Following state isn't on the public profile response (unauthenticated route) — derived from
    // whichever of this creator's listings are already loaded in the grid, same trick the previous
    // creatorName-only resolution used.
    val isFollowing = (uiState as? MarketplaceUiState.Ready)?.listings
        ?.firstOrNull { it.creatorId == creatorId }?.isFollowingCreator ?: false

    KitsunePage(
        title = creatorName ?: stringResource(R.string.creator_listings_title_fallback),
        condensedTitle = true,
        onBack = onBack
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is MarketplaceUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is MarketplaceUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_message_format, state.message), color = MaterialTheme.colorScheme.error)
                }
                is MarketplaceUiState.Ready -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CreatorProfileHeader(
                                profile = creatorProfile,
                                fallbackName = creatorName,
                                isFollowing = isFollowing,
                                onToggleFollow = { viewModel.toggleFollowCreatorInGrid(creatorId, isFollowing) }
                            )
                        }
                        if (state.listings.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.empty_listings_message),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(state.listings) { listing ->
                                ListingCard(
                                    listing,
                                    onClick = { onOpenListing(listing.id) },
                                    onToggleFollow = { viewModel.toggleFollowCreatorInGrid(listing.creatorId, listing.isFollowingCreator) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreatorProfileHeader(
    profile: CreatorProfile?,
    fallbackName: String?,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CreatorAvatar(name = profile?.creatorName ?: fallbackName, size = 56.dp)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile?.creatorName ?: fallbackName ?: stringResource(R.string.anonymous_creator_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (profile == null) {
                        Text(
                            stringResource(R.string.creator_profile_loading),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isFollowing) {
                    OutlinedButton(onClick = onToggleFollow) { Text(stringResource(R.string.unfollow_creator_button)) }
                } else {
                    Button(onClick = onToggleFollow) { Text(stringResource(R.string.follow_creator_button)) }
                }
            }

            profile?.let { p ->
                Spacer(Modifier.size(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatChip(Icons.Default.Inventory, p.listingCount.toString(), stringResource(R.string.stat_listings_label))
                    StatChip(Icons.Default.Download, p.totalDownloadCount.toString(), stringResource(R.string.stat_downloads_label))
                    StatChip(Icons.Default.Star, String.format("%.1f", p.averageRating), stringResource(R.string.stat_rating_label))
                    StatChip(Icons.Default.Group, p.followerCount.toString(), stringResource(R.string.stat_followers_label))
                }
            }
        }
    }
}
