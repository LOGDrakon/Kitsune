package com.kitsune.app.store

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.KitsuneNotice
import com.kitsune.core.designsystem.component.KitsunePage
import com.kitsune.core.designsystem.component.KitsuneSection
import com.kitsune.core.designsystem.component.NoticeTone
import com.kitsune.core.designsystem.component.OfudaPriceRow
import com.kitsune.core.designsystem.component.OfudaPriceTable
import com.kitsune.core.designsystem.component.PageTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Prices are **read from the server**, never hard-coded here.
 *
 * `BalanceResponse` carries the live per-operation costs precisely because v1 printed them as literals
 * in the UI and every server-side price change made the app lie (the image price went 5 → 3 and the
 * confirmation dialog kept saying 5). A published price list that can drift is worse than no list, so
 * this screen has no numbers of its own except the two message prices, which are structural constants
 * of the pricing model rather than tunables.
 */
@HiltViewModel
class PriceListViewModel @Inject constructor(
    private val backendClient: KitsuneBackendClient
) : ViewModel() {
    val imageCost: StateFlow<Int> = backendClient.imageCostCredits
    val imageHdCost: StateFlow<Int> = backendClient.imageHdCostCredits
    val personaGenCost: StateFlow<Int> = backendClient.personaGenCostCredits
    val universeGenCost: StateFlow<Int> = backendClient.universeGenCostCredits

    init {
        // The costs ride along with the balance response, so refreshing the balance is what keeps this
        // screen honest after a server-side price change.
        viewModelScope.launch { backendClient.refreshBalance() }
    }
}

/**
 * The whole tariff, on one screen.
 *
 * This screen exists because of a specific v1 failure: prices were only ever discovered at the moment
 * of being charged — a badge on a button, a line in a confirmation dialog, a one-off popup the first
 * time a chat was opened. A user could not answer "what does this app cost me" without using it.
 *
 * Publishing the free list alongside the paid one is the point, not padding. Roughly half of what the
 * app asks the model to do — the rolling summary, lore extraction, the chronology, the returning-user
 * recap, translating a sheet into your language, the inspiration Q&A — is paid for by the developer and
 * never billed. Stating that is what makes the paid half credible.
 */
@Composable
fun PriceListScreen(
    onBack: () -> Unit,
    viewModel: PriceListViewModel = hiltViewModel()
) {
    val imageCost by viewModel.imageCost.collectAsStateWithLifecycle()
    val imageHdCost by viewModel.imageHdCost.collectAsStateWithLifecycle()
    val personaGenCost by viewModel.personaGenCost.collectAsStateWithLifecycle()
    val universeGenCost by viewModel.universeGenCost.collectAsStateWithLifecycle()
    val spacing = KitsuneTheme.spacing

    KitsunePage(title = "Tarifs", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
                .padding(bottom = spacing.xxl)
        ) {
            PageTitle(
                text = "Tarifs",
                subtitle = "Tout ce qui coûte des Ofudas, et tout ce qui n'en coûte pas."
            )

            KitsuneSection(title = "Ce qui coûte des Ofudas") {
                OfudaPriceTable(
                    rows = listOf(
                        OfudaPriceRow("Message", 1, "Un tour de conversation."),
                        OfudaPriceRow(
                            "Message en mode Pro",
                            2,
                            "Modèle plus fort, mémoire et contexte élargis. Activable à tout moment."
                        ),
                        OfudaPriceRow("Image", imageCost, "Illustration d'une scène ou d'un personnage."),
                        OfudaPriceRow("Image HD", imageHdCost, "Modèle premium, définition supérieure."),
                        OfudaPriceRow(
                            "Génération d'un personnage",
                            personaGenCost,
                            "Par proposition demandée."
                        ),
                        OfudaPriceRow(
                            "Génération d'un univers",
                            universeGenCost,
                            "Par proposition. Un univers produit lieux, factions et PNJ d'un coup."
                        )
                    )
                )
            }

            Spacer(Modifier.height(spacing.xxl))

            KitsuneSection(title = "Ce qui est gratuit") {
                OfudaPriceTable(
                    rows = listOf(
                        OfudaPriceRow(
                            "Mémoire de l'histoire",
                            0,
                            "Résumé glissant, fiches de lore, chronologie, chapitrage."
                        ),
                        OfudaPriceRow(
                            "Récapitulatif au retour",
                            0,
                            "Ce qui s'est passé pendant ton absence, quand tu reviens sur une histoire."
                        ),
                        OfudaPriceRow(
                            "Traduction d'une fiche",
                            0,
                            "Un personnage importé est traduit dans ta langue sans frais."
                        ),
                        OfudaPriceRow(
                            "Assistant d'inspiration",
                            0,
                            "« Je ne sais pas quoi créer » : questions guidées avant de générer."
                        ),
                        OfudaPriceRow("Styles d'écriture", 0, "Les quatre styles narratifs, inclus."),
                        OfudaPriceRow("Thèmes de chronologie", 0, "Les trois habillages, inclus."),
                        OfudaPriceRow(
                            "Export en roman (PDF)",
                            0,
                            "Mise en page et couverture illustrée comprises."
                        ),
                        OfudaPriceRow("Marketplace", 0, "Parcourir, télécharger et publier des fiches.")
                    )
                )
            }

            Spacer(Modifier.height(spacing.xl))

            KitsuneNotice(
                text = "Pas d'abonnement, pas de reconduction. Tu n'achètes que ce que tu utilises, " +
                    "et 3 Ofudas te sont offerts chaque jour.",
                icon = Icons.Filled.Verified,
                tone = NoticeTone.Accent
            )

            Spacer(Modifier.height(spacing.lg))
            Text(
                text = "Les tarifs ci-dessus sont ceux appliqués par le serveur au moment où cet écran " +
                    "a été ouvert, pas des valeurs écrites dans l'application.",
                style = KitsuneTheme.type.meta,
                color = KitsuneTheme.colors.textDim
            )
        }
    }
}
