package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class BalanceResponse(
    val balance: Int,
    val lifetimeEarned: Int,
    val hasClaimedFreeCredits: Boolean = false,
    val kitsunePlus: Boolean = false,
    /** "NONE"/"PLUS"/"MAX" — which subscription is active. Perks are gated on [kitsunePlus]
     * (both tiers unlock the same things); this only says which plan, for the store's own display
     * and for the size of the monthly Ofuda grant. */
    val subscriptionTier: String = "NONE",
    val firstCreationFree: Boolean = false,
    /** Toujours `false` depuis le 2026-08-20 : la première image n'est plus offerte. Le champ est
     *  conservé pour rester compatible avec les réponses du serveur. */
    val firstImageFree: Boolean = false,
    /** Prix d'une génération d'image, en Ofudas, tel que facturé par le serveur. L'écran de
     *  génération l'affichait auparavant en dur (3), ce qui faisait mentir la confirmation de coût
     *  dès que le tarif changeait côté serveur. Le défaut ne sert que si le serveur est plus ancien
     *  que cette version de l'app. */
    val imageCostCredits: Int = 3,
    /** Prix d'une image en qualité HD. Le niveau demandé détermine côté serveur le modèle **et** le
     *  tarif : impossible d'obtenir le rendu HD au prix Standard. */
    val imageHdCostCredits: Int = 8,
    /** Prix **par proposition** d'une génération rapide de persona / d'univers. Les écrans de
     *  création affichaient le nombre de propositions comme s'il valait le nombre d'Ofudas ; depuis
     *  que les tarifs diffèrent, le coût réel est `propositions × ce montant`. Les défauts ne
     *  servent que face à un serveur plus ancien que cette version de l'app. */
    val personaGenCostCredits: Int = 3,
    val universeGenCostCredits: Int = 4,
    /** Ofudas the daily login bonus paid out on this very call — non-zero exactly once per UTC
     * day, so it can be used to trigger a "+3 Ofudas" celebration at the moment it happens. */
    val dailyCreditsGranted: Int = 0,
    /** What the daily bonus can still ever pay this account (200-credit lifetime allowance).
     * 0 means it is finished for good. */
    val dailyFreeCreditsRemaining: Int = 0
)

@Serializable
data class ClaimFreeResponse(val success: Boolean, val newBalance: Int, val error: String? = null)

@Serializable
data class TransactionResponse(
    val id: String,
    val amount: Int,
    val type: String,
    val reference: String?,
    val timestamp: String
)

@Serializable
data class HistoryResponse(val transactions: List<TransactionResponse>)

@Serializable
data class DeleteAccountResponse(val success: Boolean)