package com.kitsune.app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.backend.model.ListingSummary
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.AvatarSize
import com.kitsune.core.designsystem.component.KitsuneAvatar
import com.kitsune.core.designsystem.component.KitsuneCard
import com.kitsune.core.designsystem.component.KitsuneEmptyState
import com.kitsune.core.designsystem.component.KitsuneErrorState
import com.kitsune.core.designsystem.component.KitsuneFilterRow
import com.kitsune.core.designsystem.component.KitsunePage
import com.kitsune.core.designsystem.component.KitsuneSearchField
import com.kitsune.core.designsystem.component.KitsuneSegmented
import com.kitsune.core.designsystem.component.KitsuneSkeletonList
import com.kitsune.core.designsystem.component.KitsuneTag
import com.kitsune.core.designsystem.component.NoticeTone
import com.kitsune.core.designsystem.component.PageTitle
import com.kitsune.core.designsystem.component.rememberCondensedTitle
import com.kitsune.feature.marketplace.MarketplaceFilter
import com.kitsune.feature.marketplace.MarketplaceUiState
import com.kitsune.feature.marketplace.MarketplaceViewModel

private val SORTS = listOf("Populaires", "Mieux notés", "Récents")
private val SORT_KEYS = listOf("popular", "rating", "recent")

/**
 * Tab 3 — the community marketplace.
 *
 * Kept from v1 (explicit product decision) but re-framed. Two things changed:
 *
 * **It is no longer a peer of the user's private library.** In v1 "Marketplace" was the fourth tab of
 * the same `TabRow` as the user's own chats and personas, which put a shop inside the surface the app
 * promises is a private vault. It now has its own destination, entered deliberately.
 *
 * **One filter model instead of four.** v1 exposed type, genre, maturity and sort as four separate
 * controls, all visible at once, which is a database query form rather than a browsing experience.
 * Here the type is a segmented control (it changes *what you are looking at*), sort is a chip row (it
 * changes the order), and genre/maturity move into the search field's own filtering — so the screen
 * shows one row of controls, not four.
 */
@Composable
fun DiscoverTab(
    onOpenListing: (String) -> Unit,
    onOpenCreator: (String) -> Unit,
    viewModel: MarketplaceViewModel = hiltViewModel()
) {
    var typeSegment by rememberSaveable { mutableIntStateOf(0) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    if (!enabled) {
        KitsunePage(title = "Découvrir") { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = KitsuneTheme.spacing.gutter)
            ) {
                PageTitle(text = "Découvrir", subtitle = "Personnages et univers publiés par la communauté")
                KitsuneEmptyState(
                    icon = Icons.Outlined.Explore,
                    title = "Marketplace désactivée",
                    body = "Activez-la pour parcourir et partager des personnages et des univers. Seul ce que " +
                        "vous publiez y est envoyé ; vos conversations ne quittent jamais votre téléphone.",
                    actionLabel = "Activer la marketplace",
                    onAction = viewModel::enableMarketplace
                )
            }
        }
        return
    }
    val condensed = rememberCondensedTitle(listState)

    val type = when (typeSegment) {
        1 -> "PERSONA"
        2 -> "UNIVERSE"
        else -> null
    }

    // Debounced so typing a search term does not fire a request per keystroke. 350ms is short enough
    // to feel live and long enough that a five-letter word is one call, not five.
    LaunchedEffect(type, sortIndex, search) {
        if (search.isNotBlank()) kotlinx.coroutines.delay(350)
        viewModel.loadListings(
            MarketplaceFilter(
                type = type,
                sort = SORT_KEYS[sortIndex],
                search = search.takeIf { it.isNotBlank() }
            )
        )
    }

    KitsunePage(title = "Découvrir", condensedTitle = condensed) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = KitsuneTheme.spacing.scrollBottom),
                verticalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.md),
                modifier = Modifier.fillMaxSize()
            ) {
                item("title") {
                    Column(Modifier.padding(horizontal = KitsuneTheme.spacing.gutter)) {
                        PageTitle(
                            text = "Découvrir",
                            subtitle = "Personnages et univers publiés par la communauté"
                        )
                        // Said once, before the first request creates an anonymous account there.
                        if (authState == com.kitsune.core.backend.KitsuneBackendClient.AuthState.UNAUTHENTICATED) {
                            Text(
                                "Serveur : ${serverUrl.removePrefix("https://")} — parcourir crée un compte anonyme.",
                                style = KitsuneTheme.type.meta,
                                color = KitsuneTheme.colors.textDim,
                                modifier = Modifier.padding(bottom = KitsuneTheme.spacing.sm)
                            )
                        }
                        KitsuneSearchField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = "Rechercher un personnage, un monde…"
                        )
                        Spacer(Modifier.height(KitsuneTheme.spacing.md))
                        KitsuneSegmented(
                            options = listOf("Tout", "Personnages", "Univers"),
                            selectedIndex = typeSegment,
                            onSelect = { typeSegment = it }
                        )
                        Spacer(Modifier.height(KitsuneTheme.spacing.md))
                    }
                }
                item("sort") {
                    KitsuneFilterRow(
                        options = SORTS,
                        selected = SORTS[sortIndex],
                        onSelect = { label -> sortIndex = SORTS.indexOf(label).coerceAtLeast(0) },
                        modifier = Modifier.padding(bottom = KitsuneTheme.spacing.sm)
                    )
                }

                when (val state = uiState) {
                    is MarketplaceUiState.Loading -> item("loading") {
                        KitsuneSkeletonList(
                            modifier = Modifier.padding(horizontal = KitsuneTheme.spacing.gutter),
                            count = 5
                        )
                    }

                    is MarketplaceUiState.Error -> item("error") {
                        KitsuneErrorState(
                            title = "La bibliothèque n'a pas pu être chargée",
                            detail = state.message,
                            onRetry = { viewModel.loadListings() }
                        )
                    }

                    is MarketplaceUiState.Ready -> {
                        if (state.listings.isEmpty()) {
                            item("empty") {
                                KitsuneEmptyState(
                                    icon = Icons.Outlined.Explore,
                                    title = if (search.isBlank()) "Rien à montrer pour l'instant" else "Aucun résultat",
                                    body = if (search.isBlank()) {
                                        "Personne n'a encore publié dans cette catégorie."
                                    } else {
                                        "Essayez un autre terme, ou changez de catégorie."
                                    }
                                )
                            }
                        } else {
                            items(state.listings, key = { it.id }) { listing ->
                                ListingCard(
                                    listing = listing,
                                    onClick = { onOpenListing(listing.id) },
                                    onOpenCreator = { onOpenCreator(listing.creatorId) },
                                    modifier = Modifier.padding(horizontal = KitsuneTheme.spacing.gutter)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One listing.
 *
 * A card rather than a row because a listing is something you *evaluate* before committing — it needs
 * the description, the maturity rating and the social proof visible without a tap. The creator's name
 * is the one tappable thing inside the card besides the card itself.
 */
@Composable
private fun ListingCard(
    listing: ListingSummary,
    onClick: () -> Unit,
    onOpenCreator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KitsuneTheme.colors
    val spacing = KitsuneTheme.spacing
    KitsuneCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row {
            KitsuneAvatar(
                name = listing.title,
                size = AvatarSize.Large,
                shape = if (listing.type == "UNIVERSE") KitsuneTheme.shape.sm else KitsuneTheme.shape.md
            )
            Spacer(Modifier.width(spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = listing.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listing.creatorName ?: "Créateur anonyme",
                    style = KitsuneTheme.type.meta,
                    color = colors.accent,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable(onClick = onOpenCreator)
                        .padding(vertical = 2.dp)
                )
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = listing.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(spacing.md))
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            if (listing.genre.isNotBlank()) KitsuneTag(text = listing.genre)
            if (listing.maturityRating.isNotBlank() && listing.maturityRating != "GENERAL") {
                KitsuneTag(text = listing.maturityRating, tone = NoticeTone.Warn)
            }
            Spacer(Modifier.weight(1f))
            Stat(icon = Icons.Filled.Download, value = compactCount(listing.downloadCount))
            if (listing.reviewCount > 0) {
                Spacer(Modifier.width(spacing.md))
                Stat(
                    icon = Icons.Filled.Star,
                    value = String.format(java.util.Locale.getDefault(), "%.1f", listing.averageRating)
                )
            }
        }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = KitsuneTheme.colors.textDim, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(value, style = KitsuneTheme.type.meta, color = KitsuneTheme.colors.textDim)
    }
}

/** 1 234 → "1,2k". Keeps the stat row from reflowing as numbers grow. */
private fun compactCount(count: Int): String = when {
    count < 1000 -> count.toString()
    count < 10_000 -> String.format(java.util.Locale.getDefault(), "%.1fk", count / 1000f)
    else -> "${count / 1000}k"
}
