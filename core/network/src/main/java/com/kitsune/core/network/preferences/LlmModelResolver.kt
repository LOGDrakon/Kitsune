package com.kitsune.core.network.preferences

import com.kitsune.core.network.catalog.ModelCatalogRepository
import com.kitsune.core.network.catalog.ModelInfo
import com.kitsune.core.network.provider.ProviderStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the model to use for an [LlmOperation]: the user's selection when it is still offered by
 * its provider and fits the operation, otherwise the first model that does — preferring the default
 * provider — so an operation runs instead of failing on a stale or missing selection.
 *
 * When the catalog cannot be fetched (offline, provider down), the selection is returned unchecked:
 * the call itself will then report the real problem.
 */
@Singleton
class LlmModelResolver @Inject constructor(
    private val networkPreferences: NetworkPreferences,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val providerStore: ProviderStore
) {
    suspend fun resolve(operation: LlmOperation): String {
        val configured = networkPreferences.getModelForOperation(operation)
        val catalog = modelCatalogRepository.getModels(forceRefresh = false).getOrNull()
        if (catalog.isNullOrEmpty()) return configured

        catalog.find { it.id == configured }?.takeIf { isCompatible(operation, it) }?.let { return it.id }

        val defaultProviderId = providerStore.default()?.id
        val compatible = catalog.filter { isCompatible(operation, it) && !it.deprecated }
        val onDefault = compatible.filter { it.providerId == defaultProviderId }.ifEmpty { compatible }
        return onDefault.minByOrNull { automaticRank(operation, it.id) }?.id
            ?: configured
    }

    /**
     * [ref] if the catalog still offers it for [operation] (a story's own model, say), otherwise the
     * operation's usual model — so removing a provider never leaves a story pointing at nothing.
     */
    suspend fun resolvePreferred(ref: String?, operation: LlmOperation): String {
        if (ref.isNullOrBlank()) return resolve(operation)
        val catalog = modelCatalogRepository.getModels(forceRefresh = false).getOrNull()
        if (catalog.isNullOrEmpty()) return ref
        return catalog.find { it.id == ref }?.takeIf { isCompatible(operation, it) }?.id ?: resolve(operation)
    }

    /**
     * Order of preference when nothing was chosen and the provider suggests nothing (Mammouth, Groq,
     * a custom endpoint…): solid, affordable writing models first, then anything. The first pattern
     * matched wins; lower is better. Without this the app used whatever the catalog listed first,
     * which could be a tiny model or a very expensive one.
     */
    private fun automaticRank(operation: LlmOperation, id: String): Int {
        val patterns = when (operation) {
            LlmOperation.EMBEDDING -> listOf("text-embedding-3-small", "embed")
            LlmOperation.IMAGE_GENERATION -> listOf("gemini.*image", "flux", "gpt-image", "dall")
            else -> listOf(
                "deepseek-(v3|chat|v4)", "mistral-medium", "gpt-4o-mini|gpt-4\\.1-mini|gpt-5-mini",
                "gemini.*flash", "claude.*haiku", "mistral-(large|small)", "llama.*70b", "qwen.*(72b|235b)"
            )
        }
        val lower = id.lowercase()
        val index = patterns.indexOfFirst { Regex(it).containsMatchIn(lower) }
        return if (index < 0) patterns.size else index
    }

    /** Chat, image and embedding are mutually exclusive categories, so each operation has exactly
     * one right answer. */
    private fun isCompatible(operation: LlmOperation, model: ModelInfo): Boolean = when (operation) {
        LlmOperation.IMAGE_GENERATION -> model.isImageCapable
        LlmOperation.EMBEDDING -> model.isEmbeddingCapable
        else -> model.isChatCapable
    }
}
