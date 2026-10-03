package com.kitsune.core.network.dto

import kotlinx.serialization.Serializable

/**
 * The model catalog as served by our own backend (`GET /config/model-catalog`).
 *
 * Replaces the app's original direct call to Mammouth.ai's unauthenticated `public/models`. The
 * backend still owns the fetch (it holds the OpenRouter key needed for completions), so the app keeps
 * not needing to know anything about the upstream beyond what this DTO carries.
 */
@Serializable
data class BackendModelCatalogResponse(
    val models: List<BackendCatalogModelDto> = emptyList()
)

@Serializable
data class BackendCatalogModelDto(
    /** OpenRouter's own id: `vendor/slug`, e.g. `deepseek/deepseek-v4-flash-0731` (occasionally with
     * a `:free` variant suffix). The same id admin settings store and completions are sent with. */
    val id: String,
    val providerSlug: String = "",
    val providerName: String = "",
    val modelId: String = "",
    val displayName: String = "",
    val contextLength: Int? = null,
    /**
     * Stated by the backend rather than guessed from the name — driven by OpenRouter's own
     * `architecture` metadata (see `ModelCategory` server-side), one place instead of two.
     */
    val chatCapable: Boolean = true,
    /** [ModelCategory.slug] server-side (`chat`/`image`/`embedding`/`audio`/`video`/`other`) — was
     * already present in the backend's JSON (`CatalogModel.category` serializes it unconditionally)
     * but silently dropped here for lack of a matching field, forcing [ModelInfo] to guess a
     * model's true category from its id alone. That guess is exactly what caused a real bug: an id
     * substring check for "embed" misses real OpenRouter embedding models like
     * `voyageai/voyage-code-4` or `thenlper/gte-base`, which don't contain that substring. */
    val category: String = "chat",
    val inputCostPerMillion: Double? = null,
    val outputCostPerMillion: Double? = null,
    val cachedInputCostPerMillion: Double? = null,
    /**
     * False when the backend has no price for this model. Distinct from a price of zero: OpenRouter
     * publishes a rate for nearly every model, but the rare gap must read as "price unknown" rather
     * than free.
     */
    val priced: Boolean = false,
    val deprecated: Boolean = false,
    val deprecationReplacement: String? = null
)
