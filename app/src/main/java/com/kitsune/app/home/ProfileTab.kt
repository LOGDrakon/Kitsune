package com.kitsune.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.AvatarSize
import com.kitsune.core.designsystem.component.KitsuneAvatar
import com.kitsune.core.designsystem.component.KitsuneCard
import com.kitsune.core.designsystem.component.KitsunePage
import com.kitsune.core.designsystem.component.KitsuneRow
import com.kitsune.core.designsystem.component.KitsuneSection
import com.kitsune.core.designsystem.component.OfudaBalanceBlock
import com.kitsune.core.designsystem.component.PageTitle

/**
 * Tab 4 — the user.
 *
 * Two jobs, in this order:
 *
 * 1. **Who am I here** — the name, and the creator standing that the badge system already tracks
 *    (listings, downloads, followers). v1 computed all of this and showed it only on *other people's*
 *    profiles, or behind a "Mes badges" menu item in Settings.
 * 2. **What do I have** — the Ofuda balance, and the one door to the store.
 *
 * The balance block sits second, not first. That ordering is deliberate: the first thing the user sees
 * about themselves should be what they have made, not what they have left to spend.
 */
@Composable
fun ProfileTab(
    onOpenStore: () -> Unit,
    onOpenPrices: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenFollows: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenProposals: () -> Unit,
    onOpenMyListings: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val balance by viewModel.creditBalance.collectAsStateWithLifecycle()
    val dailyRemaining by viewModel.dailyFreeRemaining.collectAsStateWithLifecycle()
    val spacing = KitsuneTheme.spacing
    val colors = KitsuneTheme.colors

    KitsunePage(title = "Profil") { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
                .padding(bottom = spacing.scrollBottom)
        ) {
            PageTitle(
                text = state.username ?: "Vous",
                subtitle = if (state.username == null) "Compte anonyme" else "Créateur"
            )

            // ---- Creator standing -------------------------------------------------------------
            val creator = state.creatorProfile
            if (creator != null && creator.listingCount > 0) {
                val ownIdForCard = viewModel.userId
                KitsuneCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = ownIdForCard?.let { id -> { onOpenMyListings(id) } }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KitsuneAvatar(
                            name = state.username ?: "Vous",
                            size = AvatarSize.Large
                        )
                        Spacer(Modifier.width(spacing.lg))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Vos publications",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.text
                            )
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                text = creatorSummary(
                                    listings = creator.listingCount,
                                    downloads = creator.totalDownloadCount,
                                    followers = creator.followerCount
                                ),
                                style = KitsuneTheme.type.meta,
                                color = colors.textDim
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = colors.textFaint
                        )
                    }
                }
                Spacer(Modifier.height(spacing.md))
            }

            // ---- Ofudas ---------------------------------------------------------------------
            OfudaBalanceBlock(
                balance = balance,
                dailyRemaining = dailyRemaining,
                onBuy = onOpenStore,
                onSeePrices = onOpenPrices,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(spacing.xxl))

            // ---- Community ------------------------------------------------------------------
            KitsuneSection(title = "Communauté") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val ownId = viewModel.userId
                    if (ownId != null) {
                        KitsuneRow(
                            title = "Mes publications",
                            meta = state.creatorProfile?.listingCount
                                ?.let { if (it == 1) "1 fiche publiée" else "$it fiches publiées" }
                                ?: "Rien publié",
                            card = false,
                            onClick = { onOpenMyListings(ownId) },
                            leading = { RowGlyph(Icons.Filled.Storefront) },
                            trailing = { Chevron() }
                        )
                    }
                    KitsuneRow(
                        title = "Mes badges",
                        meta = state.badgeCount.let {
                            when (it) {
                                0 -> "Aucun encore"
                                1 -> "1 obtenu"
                                else -> "$it obtenus"
                            }
                        },
                        card = false,
                        onClick = onOpenBadges,
                        leading = { RowGlyph(Icons.Filled.MilitaryTech) },
                        trailing = { Chevron() }
                    )
                    KitsuneRow(
                        title = "Mes abonnements",
                        meta = state.followedCount.let {
                            if (it == 0) "Aucun créateur suivi" else "$it créateurs suivis"
                        },
                        // The accent dot here means "a creator you follow published something", which is
                        // the only push-like signal the app has. It is a dot, never a count badge.
                        highlighted = state.newFollowedListings > 0,
                        card = false,
                        onClick = onOpenFollows,
                        leading = { RowGlyph(Icons.Filled.People) },
                        trailing = { Chevron() }
                    )
                }
            }

            Spacer(Modifier.height(spacing.xl))

            KitsuneSection(title = "Échanges") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KitsuneRow(
                        title = "Messages",
                        meta = if (state.unreadMessages > 0) {
                            "${state.unreadMessages} message(s) du développeur"
                        } else {
                            "Aucun message"
                        },
                        highlighted = state.unreadMessages > 0,
                        card = false,
                        onClick = onOpenMessages,
                        leading = { RowGlyph(Icons.Filled.MailOutline) },
                        trailing = { Chevron() }
                    )
                    KitsuneRow(
                        title = "Propositions",
                        meta = "Suggérer et voter les prochaines fonctionnalités",
                        card = false,
                        onClick = onOpenProposals,
                        leading = { RowGlyph(Icons.Filled.Lightbulb) },
                        trailing = { Chevron() }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        tint = KitsuneTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 2.dp).width(22.dp)
    )
}

@Composable
private fun Chevron() {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = KitsuneTheme.colors.textFaint,
        modifier = Modifier.padding(end = 4.dp)
    )
}

/** "4 fiches · 128 téléchargements · 12 abonnés", omitting whatever is zero. */
private fun creatorSummary(listings: Int, downloads: Int, followers: Int): String = listOfNotNull(
    when (listings) {
        0 -> null
        1 -> "1 fiche"
        else -> "$listings fiches"
    },
    when (downloads) {
        0 -> null
        1 -> "1 téléchargement"
        else -> "$downloads téléchargements"
    },
    when (followers) {
        0 -> null
        1 -> "1 abonné"
        else -> "$followers abonnés"
    }
).joinToString(" · ").ifBlank { "Rien publié pour l'instant" }
