package com.kitsune.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.OfudaIcon

/**
 * The money surface.
 *
 * This file is where the "not a money vacuum" decision is actually enforced, so the rules are worth
 * stating: v1 showed the Ofuda balance in the chat top bar, on the home screen, in a dialog on
 * launch, and again behind a shopping-cart icon in the app bar — four reminders of a shrinking number
 * before the user had written a sentence. It also had a "claim free credits" dialog, a "support the
 * developer" dialog, nine one-off cosmetic purchases and two subscription tiers, all reachable from
 * the main navigation.
 *
 * v2 keeps exactly three money surfaces:
 * 1. [OfudaPill] — the balance, in **one** place (the Profile tab), never in the reading flow.
 * 2. [OfudaCost] — the price of an action, shown inline *next to that action*, before it is taken.
 * 3. [OfudaPackCard] — the packs, on the one screen the user went to in order to buy something.
 *
 * There is no upsell banner component, and no "you are running low" component, deliberately. When the
 * balance genuinely cannot cover an action, [InsufficientOfudaPanel] states that plainly and shows
 * the free daily allowance first.
 */

/** The balance. One instance in the whole app. */
@Composable
fun OfudaPill(
    balance: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = KitsuneTheme.colors
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = KitsuneTheme.spacing.md, vertical = KitsuneTheme.spacing.sm)
        ) {
            OfudaIcon(size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = balance.toString(),
                style = KitsuneTheme.type.numeric,
                color = colors.accent
            )
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = KitsuneTheme.shape.pill,
            color = colors.accentContainer,
            modifier = modifier,
            content = content
        )
    } else {
        Surface(
            shape = KitsuneTheme.shape.pill,
            color = colors.accentContainer,
            modifier = modifier,
            content = content
        )
    }
}

/**
 * The price of one action, e.g. next to "Générer une image".
 *
 * Rendered dim and small on purpose: the user needs to *know* before they tap, but a price in the
 * accent colour turns every ordinary control into an advert. [affordable] = false switches it to the
 * warn tone — the only time it raises its voice.
 */
@Composable
fun OfudaCost(
    cost: Int,
    modifier: Modifier = Modifier,
    affordable: Boolean = true,
    /** "Gratuit" instead of a number — used for the operations that genuinely never bill. */
    free: Boolean = false
) {
    val colors = KitsuneTheme.colors
    if (free) {
        Text(
            text = "Gratuit",
            style = MaterialTheme.typography.labelMedium,
            color = colors.success,
            modifier = modifier
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        OfudaIcon(size = 13.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = cost.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (affordable) colors.textDim else colors.warn
        )
    }
}

/**
 * One purchasable pack.
 *
 * Shows the per-Ofuda unit price on every card, including the cheapest — so the volume discount is
 * visible rather than implied, and a user can tell at a glance that the big pack is genuinely better
 * value instead of having to trust a "BEST VALUE" sticker. [highlight] marks the recommended pack
 * with a border, not with a louder colour or a banner.
 */
@Composable
fun OfudaPackCard(
    amount: Int,
    price: String,
    unitPrice: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    label: String? = null,
    enabled: Boolean = true
) {
    val colors = KitsuneTheme.colors
    KitsuneCard(
        modifier = modifier,
        onClick = if (enabled) onClick else null,
        selected = highlight
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OfudaIcon(size = 22.dp)
            Spacer(Modifier.width(KitsuneTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = amount.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text
                    )
                    if (label != null) {
                        Spacer(Modifier.width(KitsuneTheme.spacing.sm))
                        KitsuneTag(text = label, tone = NoticeTone.Accent)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(text = unitPrice, style = KitsuneTheme.type.meta, color = colors.textDim)
            }
            Text(
                text = price,
                style = KitsuneTheme.type.numeric,
                color = if (enabled) colors.accent else colors.textFaint
            )
        }
    }
}

/**
 * Shown when the balance cannot cover what the user just tried to do.
 *
 * Order is deliberate and is the whole point of the component: the **free** route is stated first and
 * given equal visual weight, the shortfall is stated as a plain number, and the purchase is the
 * second option rather than the only one. No countdown, no "limited offer", no dismissal penalty.
 */
@Composable
fun InsufficientOfudaPanel(
    needed: Int,
    balance: Int,
    modifier: Modifier = Modifier,
    dailyRemaining: Int? = null,
    onBuy: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = KitsuneTheme.colors
    val spacing = KitsuneTheme.spacing
    Column(modifier.fillMaxWidth()) {
        Text(
            text = "Il manque ${needed - balance} Ofudas",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text
        )
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = "Cette action en coûte $needed, il t'en reste $balance.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Spacer(Modifier.height(spacing.xl))
        if (dailyRemaining != null && dailyRemaining > 0) {
            KitsuneNotice(
                text = "Tu reçois $dailyRemaining Ofudas gratuits demain, sans rien acheter.",
                icon = Icons.Filled.Schedule,
                tone = NoticeTone.Success
            )
            Spacer(Modifier.height(spacing.md))
        }
        KitsuneButton(text = "Voir les packs", onClick = onBuy)
        Spacer(Modifier.height(spacing.xs))
        KitsuneQuietButton(text = "Plus tard", onClick = onDismiss, fillWidth = true)
    }
}

/**
 * The full price list, shown once on its own screen rather than as a badge on every button.
 *
 * Every row a user can be charged for belongs here, including the ones that are free — publishing the
 * free list is what makes the paid list trustworthy.
 */
@Composable
fun OfudaPriceTable(
    rows: List<OfudaPriceRow>,
    modifier: Modifier = Modifier
) {
    val colors = KitsuneTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, KitsuneTheme.shape.md)
            .border(1.dp, colors.outline, KitsuneTheme.shape.md)
            .padding(horizontal = KitsuneTheme.spacing.lg, vertical = KitsuneTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) KitsuneDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = KitsuneTheme.spacing.md)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    if (row.detail != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(row.detail, style = KitsuneTheme.type.meta, color = colors.textDim)
                    }
                }
                OfudaCost(cost = row.cost, free = row.cost == 0)
            }
        }
    }
}

data class OfudaPriceRow(
    val label: String,
    val cost: Int,
    val detail: String? = null
)

/** A large, quiet display of the balance for the top of the Profile tab. */
@Composable
fun OfudaBalanceBlock(
    balance: Int,
    modifier: Modifier = Modifier,
    dailyRemaining: Int? = null,
    onBuy: () -> Unit,
    onSeePrices: () -> Unit
) {
    val colors = KitsuneTheme.colors
    val spacing = KitsuneTheme.spacing
    KitsuneCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OfudaIcon(size = 28.dp)
            Spacer(Modifier.width(spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = balance.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.text
                )
                Text(
                    text = if (dailyRemaining != null && dailyRemaining > 0) {
                        "Ofudas · +$dailyRemaining offerts chaque jour"
                    } else {
                        "Ofudas"
                    },
                    style = KitsuneTheme.type.meta,
                    color = colors.textDim
                )
            }
        }
        Spacer(Modifier.height(spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            KitsuneSecondaryButton(
                text = "Recharger",
                onClick = onBuy,
                compact = true,
                fillWidth = false,
                modifier = Modifier.weight(1f)
            )
            KitsuneQuietButton(
                text = "Tarifs",
                onClick = onSeePrices,
                compact = true,
                fillWidth = false
            )
        }
    }
}
