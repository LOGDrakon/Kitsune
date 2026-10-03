package com.kitsune.core.network.catalog

/**
 * A model as reported by one of the user's providers (`GET /models`), plus local speed/quality
 * overlays.
 *
 * [id] is a [com.kitsune.core.network.provider.ModelRef] — `"<providerId>::<modelId>"` — so the same
 * model id offered by two providers stays two distinct choices. [modelId] is the bare id the
 * provider itself expects on the wire.
 */
data class ModelInfo(
    val id: String,
    /** Display name of the provider (the user's own label for it). */
    val provider: String,
    val maxInputTokens: Int?,
    val maxOutputTokens: Int?,
    /** USD per million tokens. Null when the provider publishes no price (most providers other than
     * OpenRouter don't), which must read as "unknown" rather than free. */
    val inputCostPerMillionTokens: Double?,
    val outputCostPerMillionTokens: Double?,
    /** Human-readable name from the provider's catalog, when it publishes one. */
    val displayName: String = id,
    /** Derived from [category] by [ModelCategoryClassifier]. */
    val chatCapable: Boolean = true,
    /** `chat`/`image`/`embedding`/`audio`/`video`/`other`, from [ModelCategoryClassifier] — the
     * provider's stated output modalities when it publishes them (OpenRouter does), the model id
     * otherwise. Defaults to `"chat"` so it never disagrees with [chatCapable] by omission. */
    val category: String = "chat",
    /** Kept for catalogs that flag retired models; providers usually just stop listing them. */
    val deprecated: Boolean = false,
    /** Heuristic overlay (see [ModelCatalogOverrides]) — no provider reports a speed/quality signal. */
    val speedTier: SpeedTier = SpeedTier.UNKNOWN,
    val qualityTier: QualityTier = QualityTier.UNKNOWN,
    /** Local id of the provider serving this model — see [com.kitsune.core.network.provider.ProviderConfig.id]. */
    val providerId: String = "",
    /** The bare model id sent to the provider. */
    val modelId: String = com.kitsune.core.network.provider.ModelRef.modelIdOf(id),
    /** Whether the provider says this model accepts images as input (vision). */
    val supportsImageInput: Boolean = false
) {
    val contextWindowTokens: Int? get() = maxInputTokens
    val hasPricing: Boolean get() = inputCostPerMillionTokens != null && outputCostPerMillionTokens != null

    /**
     * The classifier's answer **and** a name-based backstop — both, deliberately: picking an
     * embedding model for chat was a real reported bug (`UsageProfile`'s cheapest-model fallback
     * picked `text-embedding-3-small` precisely because it is the cheapest thing in a catalog). A
     * deprecated model is excluded too.
     */
    val isChatCapable: Boolean
        get() = chatCapable && !deprecated && !looksNonChatByName

    /** Model families that cannot serve `/chat/completions` at all, matched on the id. Works on
     * OpenRouter's `vendor/slug` form too, since it matches on substrings. */
    private val looksNonChatByName: Boolean
        get() = modelId.contains("embed", ignoreCase = true) ||
            modelId.contains("whisper", ignoreCase = true) ||
            modelId.contains("dall-e", ignoreCase = true) ||
            modelId.contains("tts", ignoreCase = true) ||
            GPT_IMAGE_ONLY_PATTERN.containsMatchIn(modelId)

    /** Whether this model can generate images — either dedicated image models or chat models that also emit inline images. */
    val isImageCapable: Boolean
        get() = category == "image" || modelId.contains("image", ignoreCase = true) ||
            modelId.contains("dall-e", ignoreCase = true)

    /** Whether this model can produce vector embeddings via `/embeddings`. [category] decides
     * first, because several real embedding ids (`voyageai/voyage-code-4`, `thenlper/gte-base`,
     * `baai/bge-m3`…) don't contain "embed"; the substring is a backstop for providers that
     * publish no modality data. */
    val isEmbeddingCapable: Boolean
        get() = category == "embedding" || modelId.contains("embed", ignoreCase = true)

    private companion object {
        /** Anchored to the start of the id **or** just after OpenRouter's vendor separator (`/`):
         * ids are `vendor/slug` (e.g. `openai/gpt-image-2`), so a `^`-only anchor would silently stop
         * matching and let a dedicated image model back into the chat picker. Also tolerates a
         * trailing OpenRouter variant suffix (`:free`, `:nitro`, ...), which can follow the slug. */
        val GPT_IMAGE_ONLY_PATTERN = Regex("(?i)(?:^|/)gpt-image(-\\d+)?(?::[a-z0-9-]+)?$")
    }
}

enum class SpeedTier { FAST, MEDIUM, SLOW, UNKNOWN }

enum class QualityTier { STANDARD, HIGH, PREMIUM, UNKNOWN }
