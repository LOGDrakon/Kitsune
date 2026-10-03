package com.kitsune.app.store

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.billingclient.api.ProductDetails
import com.kitsune.core.backend.model.SkuInfo
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.KitsuneErrorState
import com.kitsune.core.designsystem.component.KitsuneNotice
import com.kitsune.core.designsystem.component.KitsunePage
import com.kitsune.core.designsystem.component.KitsuneQuietButton
import com.kitsune.core.designsystem.component.KitsuneSkeletonList
import com.kitsune.core.designsystem.component.NoticeTone
import com.kitsune.core.designsystem.component.OfudaPackCard
import com.kitsune.core.designsystem.component.OfudaPill
import com.kitsune.core.designsystem.component.PageTitle
import com.kitsune.feature.store.PurchaseFeedback
import com.kitsune.feature.store.StoreUiState
import com.kitsune.feature.store.StoreViewModel
import java.util.Locale

/**
 * The store. One screen, one kind of product.
 *
 * v1's `UnifiedStoreScreen` sold three things in three sections — Ofuda packs, two subscription tiers,
 * and nine cosmetic unlocks — which meant a user who came to top up their balance had to walk past two
 * upsells to get to it. v2 sells Ofudas, and the things that used to be cosmetics are simply included.
 *
 * Three rules this screen holds to, which together are what "not a money vacuum" means concretely:
 *
 * 1. **The free route is stated first.** The daily allowance sits above the packs, not below them.
 * 2. **The unit price is on every card**, so the volume discount is visible rather than asserted. No
 *    "BEST VALUE" sticker, no struck-through fake reference price, no countdown.
 * 3. **The full tariff is one tap away** and is published in full, including everything that is free.
 */
@Composable
fun OfudaStoreScreen(
    onBack: () -> Unit,
    onOpenPrices: () -> Unit,
    viewModel: StoreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val balance by viewModel.creditBalance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val spacing = KitsuneTheme.spacing

    LaunchedEffect(Unit) {
        viewModel.purchaseFeedback.collect { feedback ->
            val message = when (feedback) {
                is PurchaseFeedback.Success -> "Ofudas ajoutés. Merci."
                is PurchaseFeedback.Error -> feedback.message
            }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    KitsunePage(
        title = "Ofudas",
        onBack = onBack,
        actions = { OfudaPill(balance = balance) },
        bottomBar = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
                .padding(bottom = spacing.xxl)
        ) {
            PageTitle(
                text = "Recharger",
                subtitle = "Un Ofuda = un message. Rien ne s'abonne, rien ne se renouvelle."
            )

            // The free route, above the paid one. Deliberate: a user arriving here may not need to
            // spend anything, and the app should say so before it takes their money.
            KitsuneNotice(
                text = "Tu reçois 3 Ofudas gratuits chaque jour, sans rien acheter.",
                icon = Icons.Filled.Redeem,
                tone = NoticeTone.Success
            )
            Spacer(Modifier.height(spacing.xl))

            when (val state = uiState) {
                is StoreUiState.Loading -> KitsuneSkeletonList(count = 4, withAvatar = false)

                is StoreUiState.Error -> KitsuneErrorState(
                    title = "La boutique n'a pas pu être chargée",
                    detail = state.message,
                    onRetry = viewModel::reload
                )

                is StoreUiState.Ready -> {
                    val packs = state.skus.filter { it.type == "CREDITS" }.sortedBy { it.credits }
                    if (packs.isEmpty()) {
                        KitsuneErrorState(
                            title = "Aucun pack disponible",
                            detail = "Google Play n'a renvoyé aucun produit. Réessaie dans un instant.",
                            onRetry = viewModel::reload
                        )
                    } else {
                        // The best unit price in the list, computed rather than hard-coded, so the
                        // "meilleur tarif" marker can never point at the wrong card after a price
                        // change — and disappears entirely if the packs ever price uniformly again.
                        val bestUnit = packs.minOf { it.centsPerCredit }
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                            packs.forEach { sku ->
                                val product = state.products.firstOrNull { it.productId == sku.sku }
                                OfudaPackCard(
                                    amount = sku.credits,
                                    price = displayPrice(sku, product),
                                    unitPrice = unitPriceLabel(sku),
                                    highlight = sku.centsPerCredit <= bestUnit && packs.size > 1,
                                    label = if (sku.centsPerCredit <= bestUnit && packs.size > 1) {
                                        "meilleur tarif"
                                    } else {
                                        null
                                    },
                                    // No Play product means the sku exists server-side but not in the
                                    // Play Console (or Billing is unavailable). Showing it as
                                    // unbuyable is honest; hiding it would make the catalogue silently
                                    // disagree with the price list.
                                    enabled = product != null,
                                    onClick = {
                                        (context as? Activity)?.let { viewModel.purchaseProduct(it, sku) }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(spacing.xl))
            KitsuneQuietButton(
                text = "Voir tous les tarifs",
                icon = Icons.Filled.Info,
                onClick = onOpenPrices,
                accent = true,
                fillWidth = true
            )
            Spacer(Modifier.height(spacing.lg))
            Text(
                text = "Achat unique via Google Play. Les Ofudas n'expirent pas et ne sont pas " +
                    "remboursables une fois dépensés.",
                style = KitsuneTheme.type.meta,
                color = KitsuneTheme.colors.textDim
            )
        }
    }
}

/**
 * Play's own localised price when Billing has it, the catalogue's cents otherwise.
 *
 * Play is the authority: it applies the user's country, currency and any regional pricing, so a French
 * user's "7,99 €" and a Canadian user's price both come from there. The catalogue fallback exists only
 * so the list is not blank while Billing connects.
 */
private fun displayPrice(sku: SkuInfo, product: ProductDetails?): String {
    val fromPlay = product?.oneTimePurchaseOfferDetails?.formattedPrice
    if (!fromPlay.isNullOrBlank()) return fromPlay
    return formatEuros(sku.priceCents)
}

private fun unitPriceLabel(sku: SkuInfo): String {
    val perCredit = sku.centsPerCredit / 100.0
    return String.format(Locale.FRANCE, "%.3f €/Ofuda", perCredit)
}

private fun formatEuros(cents: Int): String =
    String.format(Locale.FRANCE, "%.2f €", cents / 100.0)
