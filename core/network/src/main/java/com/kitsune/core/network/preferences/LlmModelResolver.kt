package com.kitsune.core.network.preferences

import com.kitsune.core.network.catalog.ModelCatalogRepository
import com.kitsune.core.network.catalog.ModelInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the concrete model ID to use for a given [LlmOperation], taking user overrides into
 * account. When the configured model is not found in the live catalog (e.g. it was removed or the
 * catalogue failed to load), it falls back to the default chat/image/embedding model so the
 * operation still runs rather than failing silently.
 */
@Singleton
class LlmModelResolver @Inject constructor(
    private val networkPreferences: NetworkPreferences,
    private val modelCatalogRepository: ModelCatalogRepository
) {
    /** Returns the model to use, validating it against the catalog if possible. */
    suspend fun resolve(operation: LlmOperation): String {
        val configured = networkPreferences.getModelForOperation(operation)
        val catalog = modelCatalogRepository.getModels(forceRefresh = false).getOrNull()
        if (catalog.isNullOrEmpty()) return configured

        val match = catalog.find { it.id == configured }
        if (match != null && isCompatible(operation, match)) {
            return configured
        }

        // The configured model is not in the catalog anymore, or not compatible with the operation
        // (e.g. a stale preference pointing at a model that used to exist but has since been
        // repurposed); fall back to the hardcoded default models rather than user preferences that
        // may be invalid.
        val fallback = when (operation) {
            LlmOperation.IMAGE_GENERATION -> NetworkPreferences.DEFAULT_IMAGE_MODEL_ID
            // A chat model can never serve /embeddings — this branch used to be missing, so any
            // unresolved EMBEDDING request (which, before the backend started returning embedding
            // models from GET /models, was *every* one) silently fell back to a chat model instead,
            // and the embeddings call failed outright (real reported incident, see BUGS.md).
            LlmOperation.EMBEDDING -> NetworkPreferences.DEFAULT_EMBEDDING_MODEL_ID
            else -> NetworkPreferences.DEFAULT_CHAT_MODEL_ID
        }
        val fallbackMatch = catalog.find { it.id == fallback }
        if (fallbackMatch != null && isCompatible(operation, fallbackMatch)) {
            return fallbackMatch.id
        }
        // Even the hardcoded fallback isn't in the catalog, or isn't the right shape for this
        // operation (e.g. it too got retired) — find any model that actually fits the requirement
        // rather than returning a string nothing can serve.
        return catalog.firstOrNull { isCompatible(operation, it) }?.id ?: fallback
    }

    /** Whether [model] is actually usable for [operation] — the three categories that matter here
     * are mutually exclusive (a model is never both chat-capable and image- or embedding-capable in
     * this catalog), so each operation has exactly one correct answer. */
    private fun isCompatible(operation: LlmOperation, model: ModelInfo): Boolean = when (operation) {
        LlmOperation.IMAGE_GENERATION -> model.isImageCapable
        LlmOperation.EMBEDDING -> model.isEmbeddingCapable
        else -> model.isChatCapable
    }
}
