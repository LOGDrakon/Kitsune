package com.kitsune.core.network.catalog

/**
 * A model as reported by our backend's OpenRouter catalog (`GET /config/model-catalog`), plus local
 * speed/quality overlays.
 *
 * [id] is OpenRouter's own `vendor/slug` form (e.g. `deepseek/deepseek-v4-flash-0731`, occasionally
 * with a `:free` variant suffix). Sending [id] back as the model to use is what routes the request
 * correctly — the backend forwards it to OpenRouter verbatim.
 */
data class ModelInfo(
    val id: String,
    val provider: String,
    val maxInputTokens: Int?,
    val maxOutputTokens: Int?,
    /** USD per million tokens. Null when the backend has no price for this model — rare now that
     * OpenRouter publishes a rate for nearly everything, but still possible for a model whose price
     * is momentarily missing upstream, and must read as "unknown" rather than free. */
    val inputCostPerMillionTokens: Double?,
    val outputCostPerMillionTokens: Double?,
    /** Human-readable name from OpenRouter's own catalog, when it publishes one. */
    val displayName: String = id,
    /**
     * Stated by the backend (via `ModelCategory`, driven by OpenRouter's own `architecture`
     * metadata) rather than inferred here — kept in one place so the app and the admin panel cannot
     * disagree about which models are usable for chat.
     */
    val chatCapable: Boolean = true,
    /** [ModelCategory.slug] server-side (`chat`/`image`/`embedding`/`audio`/`video`/`other`), stated
     * by the backend rather than guessed here — see [isEmbeddingCapable]. Defaults to `"chat"` for
     * any [ModelInfo] built without it (older cached data, tests), matching [chatCapable]'s own
     * default so the two never disagree by omission. */
    val category: String = "chat",
    /** Vendors do retire models. OpenRouter's catalog only ever lists live models though (a retired
     * one simply stops appearing rather than staying listed with a flag), so this is always false in
     * practice today — kept as a field so a future backend change doesn't need a shape change here. */
    val deprecated: Boolean = false,
    /** Heuristic overlay (see [ModelCatalogOverrides]) — OpenRouter's catalog reports no speed/quality signal. */
    val speedTier: SpeedTier = SpeedTier.UNKNOWN,
    val qualityTier: QualityTier = QualityTier.UNKNOWN
) {
    val contextWindowTokens: Int? get() = maxInputTokens
    val hasPricing: Boolean get() = inputCostPerMillionTokens != null && outputCostPerMillionTokens != null

    /**
     * The backend's answer **and** a local name-based backstop — both, deliberately.
     *
     * The backend has the better information (OpenRouter's own `architecture` metadata, see
     * `ModelCategory` server-side) and is also the party that would reject the call, so it decides
     * inclusion. But the local exclusion is kept as defence in depth, because picking an embedding
     * model for chat was a **real reported bug**: `UsageProfile`'s cheapest-model fallback selected
     * `text-embedding-3-small` precisely because it is the cheapest thing in the catalog. Deleting
     * this check because the backend now also filters would trade a working guard for nothing — a
     * default-constructed [ModelInfo], a stale cache, or a backend that wrongly marks something
     * chat-capable would all reach that same fallback again.
     *
     * A deprecated model is excluded too: it may still be listed, but it is not somewhere to route
     * new traffic.
     */
    val isChatCapable: Boolean
        get() = chatCapable && !deprecated && !looksNonChatByName

    /** Model families that cannot serve `/chat/completions` at all, matched on the id. Works on
     * OpenRouter's `vendor/slug` form too, since it matches on substrings. */
    private val looksNonChatByName: Boolean
        get() = id.contains("embed", ignoreCase = true) ||
            id.contains("whisper", ignoreCase = true) ||
            id.contains("dall-e", ignoreCase = true) ||
            id.contains("tts", ignoreCase = true) ||
            GPT_IMAGE_ONLY_PATTERN.containsMatchIn(id)

    /** Whether this model can generate images — either dedicated image models or chat models that also emit inline images. */
    val isImageCapable: Boolean
        get() = id.contains("image", ignoreCase = true) || id.contains("dall-e", ignoreCase = true)

    /** Whether this model can produce vector embeddings via `/embeddings` — dedicated embedding
     * models only, never a chat model. Mirrors [isImageCapable]'s role for [LlmModelResolver]:
     * without it, a resolver fallback for `LlmOperation.EMBEDDING` had no way to tell a real
     * embedding model apart from an ordinary chat model and silently picked the latter (real
     * reported bug — `LlmModelResolver` fell back to `DEFAULT_CHAT_MODEL_ID` for embeddings
     * whenever the configured model wasn't found in the catalog, which every embedding model was,
     * unconditionally, before the backend started including them in `GET /models`).
     *
     * [category] (the backend's own, stated answer) decides first, deliberately **not** an id
     * substring alone: several real OpenRouter embedding ids (`voyageai/voyage-code-4`,
     * `thenlper/gte-base`, `intfloat/e5-large-v2`, `baai/bge-m3`, any `sentence-transformers`
     * vendor id) don't contain "embed" anywhere, so a substring-only check — which is exactly what
     * this property used to be — would silently exclude them and fall through to a chat model
     * fallback again. The substring stays as a defensive backstop for [category]-less data (a
     * stale cache from before the backend started sending it, or a test fixture), same
     * dual-layer philosophy as [isChatCapable]. */
    val isEmbeddingCapable: Boolean
        get() = category == "embedding" || id.contains("embed", ignoreCase = true)

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
