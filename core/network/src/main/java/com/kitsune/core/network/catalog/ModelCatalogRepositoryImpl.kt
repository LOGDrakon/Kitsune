package com.kitsune.core.network.catalog

import com.kitsune.core.common.coroutines.DispatcherProvider
import com.kitsune.core.network.api.BackendCatalogApi
import com.kitsune.core.network.dto.BackendCatalogModelDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L // 10 minutes

@Singleton
class ModelCatalogRepositoryImpl @Inject constructor(
    private val catalogApi: BackendCatalogApi,
    private val dispatchers: DispatcherProvider
) : ModelCatalogRepository {

    private val mutex = Mutex()
    private var cachedModels: List<ModelInfo>? = null
    private var cachedAtMillis: Long = 0

    override suspend fun getModels(forceRefresh: Boolean): Result<List<ModelInfo>> = mutex.withLock {
        val cached = cachedModels
        val isFresh = cached != null && (System.currentTimeMillis() - cachedAtMillis) < CACHE_TTL_MILLIS
        if (!forceRefresh && isFresh) {
            return@withLock Result.success(cached!!)
        }

        withContext(dispatchers.io) {
            runCatching {
                val response = catalogApi.listModels()
                if (!response.isSuccessful) {
                    error("Model catalog request failed: HTTP ${response.code()}")
                }
                val body = response.body() ?: error("Empty model catalog response")
                body.models.map(::toModelInfo)
            }.onSuccess {
                cachedModels = it
                cachedAtMillis = System.currentTimeMillis()
            }
        }
    }

    private fun toModelInfo(entry: BackendCatalogModelDto): ModelInfo = ModelInfo(
        // OpenRouter's own vendor/slug id, sent straight back as the model to use.
        id = entry.id,
        provider = entry.providerName.ifBlank { entry.providerSlug },
        displayName = entry.displayName.ifBlank { entry.modelId.ifBlank { entry.id } },
        maxInputTokens = entry.contextLength,
        // OpenRouter doesn't report a separate output ceiling in its catalog; the request-level
        // max_tokens is what actually bounds a reply, so this stays null rather than being invented.
        maxOutputTokens = null,
        inputCostPerMillionTokens = entry.inputCostPerMillion,
        outputCostPerMillionTokens = entry.outputCostPerMillion,
        // Stated by the backend instead of guessed from the name — driven by OpenRouter's own
        // architecture metadata (see ModelCategory server-side).
        chatCapable = entry.chatCapable,
        category = entry.category,
        deprecated = entry.deprecated,
        speedTier = ModelCatalogOverrides.speedFor(entry.modelId.ifBlank { entry.id }),
        qualityTier = ModelCatalogOverrides.qualityFor(entry.modelId.ifBlank { entry.id })
    )
}
