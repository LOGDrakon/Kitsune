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
        val compatible = catalog.filter { isCompatible(operation, it) }
        return compatible.firstOrNull { it.providerId == defaultProviderId }?.id
            ?: compatible.firstOrNull()?.id
            ?: configured
    }

    /** Chat, image and embedding are mutually exclusive categories, so each operation has exactly
     * one right answer. */
    private fun isCompatible(operation: LlmOperation, model: ModelInfo): Boolean = when (operation) {
        LlmOperation.IMAGE_GENERATION -> model.isImageCapable
        LlmOperation.EMBEDDING -> model.isEmbeddingCapable
        else -> model.isChatCapable
    }
}
