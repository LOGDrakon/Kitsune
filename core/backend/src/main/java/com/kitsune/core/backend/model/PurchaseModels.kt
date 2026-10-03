package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class SkuInfo(
    val sku: String,
    val type: String,
    val credits: Int,
    val priceCents: Int,
    val currency: String,
    val description: String,
    /**
     * Whether the backend still advertises this product.
     *
     * `GET /purchases/catalog` already filters to offered products only, so in practice everything
     * the app receives is `true` — the field is mirrored here so the store can *state* the fact
     * rather than assume it, and so a legacy product resolved by sku (a restored purchase) can be
     * recognised as retired instead of being rendered as something still on sale.
     */
    val offered: Boolean = true
) {
    /** Cents per Ofuda — what the store prints under each pack so the volume discount is visible. */
    val centsPerCredit: Double get() = if (credits > 0) priceCents.toDouble() / credits else 0.0
}

@Serializable
data class SkuCatalogResponse(val skus: List<SkuInfo>)

@Serializable
data class VerifyPurchaseRequest(
    val sku: String,
    val purchaseToken: String,
    val orderId: String
)

@Serializable
data class PurchaseVerifyResponse(
    val success: Boolean,
    val creditsAdded: Int = 0,
    val newBalance: Int = 0,
    val error: String? = null
)
