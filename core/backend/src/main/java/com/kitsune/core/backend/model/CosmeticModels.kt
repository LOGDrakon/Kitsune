package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class CosmeticItem(
    val id: String,
    val name: String,
    val category: String,
    val priceCredits: Int,
    val description: String,
    val previewUrl: String? = null,
    val isAvailable: Boolean = true,
    val stylePrompt: String? = null
)

@Serializable
data class CosmeticCatalogResponse(val items: List<CosmeticItem>)

@Serializable
data class OwnedCosmeticsResponse(val cosmeticIds: List<String>)
