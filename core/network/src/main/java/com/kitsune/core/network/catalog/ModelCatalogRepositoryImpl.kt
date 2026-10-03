package com.kitsune.core.network.catalog

import com.kitsune.core.network.provider.LlmHttpClient
import com.kitsune.core.network.provider.ModelRef
import com.kitsune.core.network.provider.ProviderConfig
import com.kitsune.core.network.provider.ProviderStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L // 10 minutes

/**
 * The live catalog: every model of every provider the user has configured, fetched straight from
 * each provider's `GET /models`.
 *
 * A provider that fails to answer is skipped rather than failing the whole catalog, so one expired
 * key does not empty every picker. Only when *every* provider fails is the result a failure.
 */
@Singleton
class ModelCatalogRepositoryImpl @Inject constructor(
    private val httpClient: LlmHttpClient,
    private val providerStore: ProviderStore
) : ModelCatalogRepository {

    private val mutex = Mutex()
    private var cachedModels: List<ModelInfo>? = null
    private var cachedFor: List<ProviderConfig> = emptyList()
    private var cachedAtMillis: Long = 0

    override suspend fun getModels(forceRefresh: Boolean): Result<List<ModelInfo>> = mutex.withLock {
        val providers = providerStore.all()
        if (providers.isEmpty()) return@withLock Result.success(emptyList())

        val cached = cachedModels
        val isFresh = cached != null && cachedFor == providers &&
            (System.currentTimeMillis() - cachedAtMillis) < CACHE_TTL_MILLIS
        if (!forceRefresh && isFresh) return@withLock Result.success(cached!!)

        val perProvider = coroutineScope {
            providers.map { provider ->
                async { runCatching { httpClient.listModels(provider).mapNotNull { toModelInfo(provider, it) } } }
            }.awaitAll()
        }
        if (perProvider.all { it.isFailure }) {
            return@withLock Result.failure(perProvider.first().exceptionOrNull()!!)
        }
        val models = perProvider.flatMap { it.getOrDefault(emptyList()) }
        cachedModels = models
        cachedFor = providers
        cachedAtMillis = System.currentTimeMillis()
        Result.success(models)
    }

    private fun toModelInfo(provider: ProviderConfig, entry: JsonObject): ModelInfo? {
        val rawId = entry.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val architecture = entry["architecture"] as? JsonObject
        val outputs = architecture?.strings("output_modalities").orEmpty()
        val inputs = architecture?.strings("input_modalities").orEmpty()
        val category = ModelCategoryClassifier.classify(rawId, outputs)
        val pricing = entry["pricing"] as? JsonObject
        // OpenRouter publishes per-token USD as strings; scaled to per-million for display.
        fun perMillion(key: String) = pricing?.str(key)?.toDoubleOrNull()?.takeIf { it >= 0 }?.times(1_000_000)
        val context = entry.str("context_length")?.toIntOrNull()
            ?: entry.str("context_window")?.toIntOrNull()
            ?: entry.str("max_context_length")?.toIntOrNull()
            ?: entry.str("max_model_len")?.toIntOrNull()

        return ModelInfo(
            id = ModelRef.of(provider.id, rawId),
            provider = provider.name,
            providerId = provider.id,
            modelId = rawId,
            displayName = entry.str("name")?.takeIf { it.isNotBlank() } ?: rawId,
            maxInputTokens = context,
            maxOutputTokens = null,
            inputCostPerMillionTokens = perMillion("prompt"),
            outputCostPerMillionTokens = perMillion("completion"),
            chatCapable = category == "chat",
            category = category,
            speedTier = ModelCatalogOverrides.speedFor(rawId),
            qualityTier = ModelCatalogOverrides.qualityFor(rawId),
            supportsImageInput = "image" in inputs.map { it.lowercase() }
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
}
